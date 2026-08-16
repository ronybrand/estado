# Deploy — configs versionadas

Cópia versionada do que roda na EC2 fora do jar da aplicação: stacks Docker Compose, o script de
rolling swap, o script de backup e as units do systemd. Até este diretório existir, essas peças
viviam só no servidor (`~/estado/`, `~/proxy/`, `/etc/systemd/system/`), digitadas/editadas
manualmente por SSH e sem histórico — ver Fase 3/6 do plano de deploy e
[ADR 0004](../docs/adr/0004-deploy-pull-via-systemd-timer.md) /
[ADR 0005](../docs/adr/0005-rolling-swap-sem-canario-blue-green.md) /
[ADR 0006](../docs/adr/0006-backup-pg-dump-s3.md) /
[ADR 0008](../docs/adr/0008-rollback-manual-por-tag-registrada.md) /
[ADR 0009](../docs/adr/0009-prune-semanal-de-imagens-dangling.md).

## Importante: não há pipeline puxando o estado real da EC2 pra cá

Depois de escrever esses arquivos a partir do comportamento documentado (plano de deploy + ADRs +
case study), foi feito um diff real via SSH contra o servidor (2026-08-15) e as divergências
encontradas foram corrigidas aqui — ver detalhe abaixo. Mas isso é uma reconciliação pontual, não
sincronização contínua: qualquer mudança futura direto no servidor (por SSH, sem passar por aqui)
volta a divergir sem aviso. Antes de confiar cegamente neste diretório de novo depois de um tempo
sem tocar nele, vale repetir o diff:

```
ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248 'cat ~/estado/docker-compose.yml'
```

Depois de alinhado, trate este diretório como fonte da verdade e replique manualmente pro servidor
a cada mudança (deploy dessas configs continua sendo manual, deliberadamente — ver ADR 0004: não
existe canal automatizado de push pra dentro da EC2).

**Divergências reais encontradas na reconciliação de 2026-08-15** (o que estava errado aqui antes
de comparar com o servidor):
- Rede do Postgres/app é `estado_internal` (nome fixado explicitamente no `docker-compose.yml` via
  `networks.internal.name`), não `internal` — `deploy.sh`/`lib-swap.sh` referenciavam o nome errado,
  o que teria feito o `docker run` do container novo falhar por completo no próximo deploy.
- `POSTGRES_DB`/`POSTGRES_USER` não são variáveis de `.env` — vêm fixos como `estado` no
  `docker-compose.yml` e nos scripts. Os scripts aqui referenciavam `${POSTGRES_DB}`/`${POSTGRES_USER}`
  vazios, que teriam gerado uma JDBC URL quebrada.
- As units systemd reais rodam como `User=ec2-user Group=docker`, não como root (padrão quando
  `User=` não é definido) — corrigido.
- O health check real sobe um container `curlimages/curl` efêmero na rede `portfolio` e testa de
  fora (valida DNS/rede do Compose, não só "processo respondeu"), em vez de `docker exec` + `wget`
  de dentro do container — adotado o mecanismo real, já provado em produção.
- Existia um `~/estado/Caddyfile` (`reverse_proxy app:8080`) esquecido no servidor — resquício de
  antes da Fase 7, quando o Caddy ainda fazia parte do stack `~/estado/`. Não era usado por nada (o
  Caddy ativo é o de `~/proxy/`), não foi versionado aqui de propósito, e já foi apagado do servidor
  na mesma sessão desta reconciliação.

**Desde então** (mesma sessão, 2026-08-15): `deploy.sh`/`rollback.sh`/`lib-swap.sh`/`backup.sh`
sincronizados de verdade na EC2 (não só reconciliados no repo) via `scp`, `estado-prune.timer`
aplicado e habilitado no servidor (ADR 0009), e o mecanismo de rollback testado de ponta a ponta
contra imagens reais do GHCR — ver ADR 0008. Este README descreve o layout atual; o histórico
completo de validação fica nos commits, não repetido aqui.

## Layout

| Neste repo | Na instância | O que é |
|---|---|---|
| `deploy/estado/docker-compose.yml` | `~/estado/docker-compose.yml` | Só o Postgres (o container da app não é mais gerenciado pelo Compose, ver ADR 0005) |
| `deploy/estado/deploy.sh` | `~/estado/deploy.sh` | Rolling swap: sobe a imagem nova, espera `/actuator/health`, só então troca. Grava a tag anterior em `last-good-tag` |
| `deploy/estado/rollback.sh` | `~/estado/rollback.sh` | Reaplica uma tag anterior (padrão: `last-good-tag`) com o mesmo swap — ver [ADR 0008](../docs/adr/0008-rollback-manual-por-tag-registrada.md) |
| `deploy/estado/lib-swap.sh` | `~/estado/lib-swap.sh` | Lógica de swap compartilhada entre `deploy.sh` e `rollback.sh` |
| `deploy/estado/backup.sh` | `~/estado/backup.sh` | `pg_dump` diário comprimido, valida tamanho mínimo, envia pro S3 |
| `deploy/estado/.env.example` | `~/estado/.env` (real, `chmod 600`) | Template — segredo real nunca vai pro repo, fica só na instância + Bitwarden |
| `deploy/proxy/docker-compose.yml` | `~/proxy/docker-compose.yml` | Caddy, stack compartilhada entre apps do portfólio |
| `deploy/proxy/Caddyfile` | `~/proxy/Caddyfile` | Roteamento HTTPS, inclui o fix do prefixo `/api` |
| `deploy/systemd/*.service`, `*.timer` | `/etc/systemd/system/` | Timers de deploy (5 min), backup (diário 06:00 UTC) e prune de imagens dangling (semanal) |

## Aplicar uma mudança na instância

```
scp -i ~/.ssh/estado-key.pem deploy/estado/docker-compose.yml deploy/estado/deploy.sh deploy/estado/rollback.sh deploy/estado/lib-swap.sh deploy/estado/backup.sh ec2-user@54.94.231.248:~/estado/
scp -i ~/.ssh/estado-key.pem deploy/proxy/docker-compose.yml deploy/proxy/Caddyfile ec2-user@54.94.231.248:~/proxy/

scp -i ~/.ssh/estado-key.pem deploy/systemd/estado-deploy.* deploy/systemd/estado-backup.* deploy/systemd/estado-prune.* ec2-user@54.94.231.248:/tmp/
ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248 '
  sudo mv /tmp/estado-*.service /tmp/estado-*.timer /etc/systemd/system/ &&
  sudo systemctl daemon-reload
'
```

Redes externas compartilhadas (`internal`, `portfolio`) e os recursos de AWS (bucket S3, IAM role,
Security Group, alarme do CloudWatch) não são arquivo — continuam documentados nas ADRs e no plano
de deploy, não neste diretório.

## Adicionar um novo app de portfólio

O padrão pra replicar (`<app>/docker-compose.yml`, bloco novo no `Caddyfile`, timer próprio) está
descrito na seção "Como adicionar um novo app de portfólio depois" do plano de deploy — não
duplicado aqui pra não ter duas fontes divergindo.
