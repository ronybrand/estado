# Deploy — configs versionadas

Cópia versionada do que roda *dentro* da EC2, fora do jar da aplicação: Docker Compose, os scripts de
deploy/rollback/backup, e as units systemd. Recursos da própria AWS (instância, rede, IAM, S3, alarme)
são gerenciados por Terraform em [`../terraform/`](../terraform/) — este diretório cobre só o lado de
dentro da instância, que o Terraform não gerencia de propósito (ver
[ADR 0011](../docs/adr/0011-terraform-import-sem-terragrunt.md)).

**Acesso à instância**: SSH restrito por IP (`admin_cidr` no Terraform — precisa reaplicar toda vez
que o IP do administrador mudar) é o caminho principal, usado pros comandos `scp`/`ssh` abaixo. Como
alternativa que não depende de IP nenhum (útil em rede móvel, que às vezes bloqueia a porta 22 de
saída), a instância também aceita **AWS Systems Manager Session Manager**
(`aws ssm start-session --target i-07cffc5ce75898ad0 --region sa-east-1`, requer o
[Session Manager Plugin](https://s3.amazonaws.com/session-manager-downloads/plugin/latest/windows/SessionManagerPluginSetup.exe)
instalado localmente) — login como `ssm-user`, não `ec2-user`, o que muda caminhos tipo
`~/alloy/.env`. `scp` não funciona por SSM; nesse caso, criar arquivo via heredoc
(`cat > arquivo << 'EOF' ... EOF`) direto na sessão.

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
| `deploy/bootstrap/bootstrap.sh` | Rodado uma vez, não copiado | Reproduz numa instância nova o que hoje só existe feito à mão na atual: Docker/Compose, redes, swap de 1 GB, limites do journald, rotação de log do Docker, diretórios de app e a instalação do Alloy — ver fase 5.0 de [`docs/plano-dominio-proprio.md`](../docs/plano-dominio-proprio.md) |

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
  sudo usermod -aG systemd-journal alloy &&
  sudo mkdir -p /etc/systemd/system/alloy.service.d &&
  echo -e "[Service]\nEnvironmentFile=/home/ec2-user/alloy/.env" | sudo tee /etc/systemd/system/alloy.service.d/override.conf &&
  sudo systemctl daemon-reload &&
  sudo systemctl enable --now alloy &&
  sudo systemctl status alloy --no-pager
'
```

Se o acesso for via SSM em vez de SSH, o usuário logado é `ssm-user`, não `ec2-user` — o
`EnvironmentFile` no override precisa apontar pra `/home/ssm-user/alloy/.env` nesse caso (confirmar
com `pwd`/`whoami` na sessão antes de escrever o override, os dois caminhos coexistem sem problema
dependendo de por onde a instalação foi feita).

`usermod -aG docker alloy` é o que dá ao agente permissão de ler `/var/run/docker.sock` pra coletar
logs dos containers (`loki.source.docker`) — sem isso, essa parte falha silenciosamente.
`usermod -aG systemd-journal alloy` é o equivalente pro journal do systemd (`loki.source.journal`,
usado pelos logs de `backup.sh`/`deploy.sh`/`prune.sh`, que rodam no host, não em container) — bem
mais restrito que o grupo `docker`, só leitura do journal. Mudança de grupo só é aplicada em processos
novos, por isso os dois `usermod` precisam vir antes do primeiro `systemctl enable --now`, não depois.

**Validado de ponta a ponta em produção** (2026-08-16, via SSM): `journalctl -u alloy` sem nenhum erro
de autenticação/conexão/permissão; métrica de host (`up{job="integrations/node_exporter"}`), métrica
da app (`up{job="estado-app"}`, IP dinâmico do container resolvido certo via `discovery.docker`), log
de container (`{container="estado-app"}`) e log de systemd (`{job="integrations/systemd-journal"}`,
com o label `unit` populado corretamente) confirmados aparecendo no Explore do Grafana Cloud com dado
real. O `relabel_rules` do `loki.source.journal` (a única sintaxe não testada antes) funcionou sem
ajuste.

**Alertas criados e validados** (2026-08-16) — os dois de `estado-5xx` e `estado-backup-ausente`, spec
completa na [ADR 0012](../docs/adr/0012-grafana-cloud-alloy-observabilidade.md#decis%C3%A3o). Achado
na validação: "Alert state if no data" precisa ser `OK` no alerta de 5xx (não o padrão `NoData`) —
`sum()` sobre uma métrica sem nenhuma ocorrência retorna vazio, não zero, e com `NoData` isso
disparava notificação todo santo minuto sem 5xx nenhum acontecer.

Preencher `deploy/estado/.env` com `GRAFANA_CLOUD_URL`/`GRAFANA_CLOUD_ANNOTATIONS_TOKEN` (Service
Account token do Grafana, papel Editor, escopo diferente do token de métrica/log) ativa a anotação de
deploy — feito via `sudo tee -a /home/ec2-user/estado/.env` (caminho absoluto, não `~`, se o acesso
for por SSM — usuário `ssm-user` não tem permissão de escrita no `.env` do `ec2-user` sem `sudo`).

Preencher `GITHUB_DEPLOY_TOKEN` no mesmo `.env` (fine-grained, só o repo `estado`, só *Deployments:
Read and write*) faz `deploy.sh`/`rollback.sh` registrarem `success`/`failure` na aba Deployments do
GitHub. Sem o token, ou com a API fora do ar, o deploy segue normalmente. Falha repetida da mesma
imagem só é registrada uma vez (`last-failed-image`), já que o timer reexecuta a cada 5 min.

Isso também significa que uma mudança feita direto no servidor por SSH, sem passar por aqui, faz este
diretório divergir sem aviso. Pra conferir se ainda bate com a realidade:

```
ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248 'cat ~/estado/docker-compose.yml'
```

## Adicionar um novo app de portfólio

O padrão pra replicar (`<app>/docker-compose.yml`, bloco novo no `Caddyfile`, timer próprio) está
descrito na seção "Como adicionar um novo app de portfólio depois" do plano de deploy — não duplicado
aqui pra não ter duas fontes divergindo.

## Memória, native image e variáveis do `lib-swap.sh`

O `lib-swap.sh` aplica limites de memória ao backend (e o do agent, ao agent). Os valores têm
default no próprio script e podem ser sobrescritos no `.env` do servidor:

| Variável | Default | Efeito |
|---|---|---|
| `APP_JAVA_OPTS` | SerialGC, `-Xmx192m`, Metaspace 128m, heap free ratio 10/20 | `JAVA_TOOL_OPTIONS` do backend (ignorado pela imagem native) |
| `APP_MEMORY_LIMIT` | `384m` | `--memory` do container |
| `DB_POOL_MAX` / `DB_POOL_MIN_IDLE` | `4` / `2` | Pool do Hikari (o padrão do Spring mantém 10 conexões abertas) |
| `DRAIN_SECONDS` | `60` | Quanto o container antigo continua no ar depois do swap (ver abaixo) |

O health check do swap espera até 180 s (90 x 2 s), porque sob pressão de memória a JVM nova sobe
mais devagar.

**Drenagem do container antigo** ([ADR 0023](../docs/adr/0023-drenar-container-antigo-no-swap.md)):
depois do swap, o container antigo fica renomeado como `<nome>-antigo` por `DRAIN_SECONDS` antes de
ser parado e removido, para não dar 502 em quem ainda tem o IP dele em cache. Por isso um deploy
leva ~60 s a mais, e um `docker ps` durante esse tempo mostra os dois. Um `-antigo` que sobrar de
uma execução interrompida é removido no início do próximo swap.

**Limpeza de imagens a cada deploy:** no fim da drenagem o script roda `docker image prune -f`, e a
imagem que acabou de sair ganha antes a tag local `anterior` (`marcar_anterior`), para não ser apagada.
Isso mantém o disco perto de ~7 GB, em vez de crescer uma imagem por deploy até o prune semanal. E dá
uma volta que não depende do registry: `./rollback.sh anterior` usa a imagem local. Sem argumento, o
`rollback.sh` baixa a revisão de `last-good-tag` do GHCR; se o registry não responder, usa a `anterior`
só quando ela for exatamente essa revisão (depois de um rollback ela aponta para a versão ruim).

**Imagem native e volta para a JVM** (ver [ADR 0022](../docs/adr/0022-native-image-graalvm.md)): a
`latest` é a imagem native e o `estado-deploy.timer` a puxa sozinho. O `docker-publish` publica a JVM
em paralelo como `jvm-<sha>` e `jvm-latest`. Para voltar à JVM: **parar e desabilitar o timer**
(`systemctl disable --now estado-deploy.timer`, senão ele troca de volta para a `latest` native no
próximo tick, e `disable` é necessário para sobreviver a um reboot) e `./rollback.sh jvm-<sha>`. Para
retomar o fluxo automático: `systemctl enable --now estado-deploy.timer`.

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
