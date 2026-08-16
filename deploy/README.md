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
```

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
