# Deploy — configs versionadas

Cópia versionada do que roda *dentro* da EC2, fora do jar da aplicação: Docker Compose, os scripts de
deploy/rollback/backup, e as units systemd. Recursos da própria AWS (instância, rede, IAM, S3, alarme)
são gerenciados por Terraform em [`../terraform/`](../terraform/) — este diretório cobre só o lado de
dentro da instância, que o Terraform não gerencia de propósito (ver
[ADR 0011](../docs/adr/0011-terraform-import-sem-terragrunt.md)).

## Layout

| Neste repo | Na instância | O que é |
|---|---|---|
| `deploy/estado/docker-compose.yml` | `~/estado/docker-compose.yml` | Só o Postgres (o container da app não é gerenciado pelo Compose, ver ADR 0005) |
| `deploy/estado/deploy.sh` | `~/estado/deploy.sh` | Rolling swap: sobe a imagem nova, espera `/actuator/health`, só então troca. Grava a tag anterior em `last-good-tag` |
| `deploy/estado/rollback.sh` | `~/estado/rollback.sh` | Reaplica uma tag anterior (padrão: `last-good-tag`) com o mesmo swap — ver [ADR 0008](../docs/adr/0008-rollback-manual-por-tag-registrada.md) |
| `deploy/estado/lib-swap.sh` | `~/estado/lib-swap.sh` | Lógica de swap compartilhada entre `deploy.sh` e `rollback.sh` |
| `deploy/estado/backup.sh` | `~/estado/backup.sh` | `pg_dump` diário comprimido, valida tamanho mínimo, envia pro S3 |
| `deploy/estado/.env.example` | `~/estado/.env` (real, `chmod 600`) | Template — segredo real nunca vai pro repo, fica só na instância + Bitwarden |
| `deploy/proxy/docker-compose.yml` | `~/proxy/docker-compose.yml` | Caddy, stack compartilhada entre apps do portfólio |
| `deploy/proxy/Caddyfile` | `~/proxy/Caddyfile` | Roteamento HTTPS, inclui o fix do prefixo `/api` |
| `deploy/alloy/config.alloy` | `/etc/alloy/config.alloy` | Coleta métricas de host (disco/memória/CPU) e empurra pro Grafana Cloud — ver [ADR 0012](../docs/adr/0012-grafana-cloud-alloy-observabilidade.md) |
| `deploy/alloy/.env.example` | `~/alloy/.env` (real, `chmod 600`) | Template — credenciais do Grafana Cloud, nunca vão pro repo |
| `deploy/systemd/*.service`, `*.timer` | `/etc/systemd/system/` | Timers de deploy (5 min), backup (diário 06:00 UTC) e prune de imagens dangling (semanal, ADR 0009) |

Redes Docker (`estado_internal`, `portfolio`) não são arquivo — criadas pelo Compose/`docker network
create`, documentadas nas ADRs.

## Sem sincronização automática

Nada aqui é empurrado pra EC2 sozinho — deploy dessas configs continua manual, deliberadamente (ver
ADR 0004: não existe canal automatizado de push pra dentro da instância). Depois de editar um arquivo:

```
scp -i ~/.ssh/estado-key.pem deploy/estado/docker-compose.yml deploy/estado/deploy.sh deploy/estado/rollback.sh deploy/estado/lib-swap.sh deploy/estado/backup.sh ec2-user@54.94.231.248:~/estado/
scp -i ~/.ssh/estado-key.pem deploy/proxy/docker-compose.yml deploy/proxy/Caddyfile ec2-user@54.94.231.248:~/proxy/

scp -i ~/.ssh/estado-key.pem deploy/systemd/estado-deploy.* deploy/systemd/estado-backup.* deploy/systemd/estado-prune.* ec2-user@54.94.231.248:/tmp/
ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248 '
  sudo mv /tmp/estado-*.service /tmp/estado-*.timer /etc/systemd/system/ &&
  sudo systemctl daemon-reload
'

scp -i ~/.ssh/estado-key.pem deploy/alloy/config.alloy ec2-user@54.94.231.248:/tmp/
ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248 'sudo mv /tmp/config.alloy /etc/alloy/config.alloy && sudo systemctl restart alloy'
```

## Instalar o Grafana Alloy (uma vez)

Ver [ADR 0012](../docs/adr/0012-grafana-cloud-alloy-observabilidade.md). Antes de tudo, criar uma
conta free no [Grafana Cloud](https://grafana.com/auth/sign-up/create-user) e pegar, em *Connections
→ Add new connection → Hosted Prometheus metrics*, a URL de `remote_write`, o instance ID, e um API
token com escopo `metrics:write` (em *Access Policies*).

```
ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248 '
  wget -q -O /tmp/gpg.key https://rpm.grafana.com/gpg.key &&
  sudo rpm --import /tmp/gpg.key &&
  echo -e "[grafana]\nname=grafana\nbaseurl=https://rpm.grafana.com\nrepo_gpgcheck=1\nenabled=1\ngpgcheck=1\ngpgkey=https://rpm.grafana.com/gpg.key\nsslverify=1\nsslcacert=/etc/pki/tls/certs/ca-bundle.crt" | sudo tee /etc/yum.repos.d/grafana.repo &&
  sudo dnf install -y alloy
'

# preencher deploy/alloy/.env.example com os valores reais antes deste passo
scp -i ~/.ssh/estado-key.pem deploy/alloy/config.alloy ec2-user@54.94.231.248:/tmp/
scp -i ~/.ssh/estado-key.pem <caminho-local-do-.env-preenchido> ec2-user@54.94.231.248:/tmp/.env

ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248 '
  sudo mv /tmp/config.alloy /etc/alloy/config.alloy &&
  mkdir -p ~/alloy && mv /tmp/.env ~/alloy/.env && chmod 600 ~/alloy/.env &&
  sudo usermod -aG docker alloy &&
  sudo mkdir -p /etc/systemd/system/alloy.service.d &&
  echo -e "[Service]\nEnvironmentFile=/home/ec2-user/alloy/.env" | sudo tee /etc/systemd/system/alloy.service.d/override.conf &&
  sudo systemctl daemon-reload &&
  sudo systemctl enable --now alloy &&
  sudo systemctl status alloy --no-pager
'
```

`usermod -aG docker alloy` é o que dá ao agente permissão de ler `/var/run/docker.sock` pra coletar
logs dos containers (`loki.source.docker`) — sem isso, essa parte falha silenciosamente. Mudança de
grupo só é aplicada em processos novos, por isso o `usermod` precisa vir antes do primeiro
`systemctl enable --now`, não depois.

Validar: `journalctl -u alloy -n 50 --no-pager` sem erro de autenticação/conexão/permissão no socket,
métricas aparecendo em Grafana Cloud → Explore (filtrando por `job="integrations/node_exporter"`), e
logs aparecendo em Explore → Loki (filtrando por `container="estado-app"` etc.). O comando
`dnf install alloy` acima assume que o pacote cria o unit em `/etc/alloy/config.alloy` e usuário/grupo
`alloy` — confirmar isso no output da instalação antes do `systemctl enable`, já que não validei esse
passo numa instância real (SSH bloqueado nesta sessão, ver histórico da conversa).

Depois de confirmado que os dados estão chegando: criar o alerta de 5xx na UI do Grafana Cloud
(Alerting → New alert rule) com a query/limiar exatos documentados na
[ADR 0012](../docs/adr/0012-grafana-cloud-alloy-observabilidade.md#decis%C3%A3o).

Isso também significa que uma mudança feita direto no servidor por SSH, sem passar por aqui, faz este
diretório divergir sem aviso. Pra conferir se ainda bate com a realidade:

```
ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248 'cat ~/estado/docker-compose.yml'
```

## Adicionar um novo app de portfólio

O padrão pra replicar (`<app>/docker-compose.yml`, bloco novo no `Caddyfile`, timer próprio) está
descrito na seção "Como adicionar um novo app de portfólio depois" do plano de deploy — não duplicado
aqui pra não ter duas fontes divergindo.

## Histórico

A primeira versão deste diretório foi escrita a partir da documentação (não do servidor real) e tinha
divergências reais que teriam quebrado o deploy se sincronizadas sem checar — detalhe completo no
commit `7d42c48` e no [ADR 0008](../docs/adr/0008-rollback-manual-por-tag-registrada.md). Já
corrigido; o layout acima reflete o estado atual, reconciliado e sincronizado com a instância real.

Essa reconciliação trouxe o repo pra bater com o servidor, mas o servidor real também tinha regredido
duas coisas sem ninguém notar: `lib-swap.sh` perdeu o `docker logs` no path de falha do health check
(diagnosticar um deploy quebrado ficou sem informação nenhuma), e o container efêmero do health check
passou a usar `curlimages/curl:latest` sem pin de versão. Achado numa revisão de código posterior,
corrigido e sincronizado de volta pro servidor — `docker logs` restaurado, imagem pinada em
`curlimages/curl:8.11.1`.
