# Deploy — configs versionadas

Cópia versionada do que roda na EC2 fora do jar da aplicação: stacks Docker Compose, o script de
rolling swap, o script de backup e as units do systemd. Até este diretório existir, essas peças
viviam só no servidor (`~/estado/`, `~/proxy/`, `/etc/systemd/system/`), digitadas/editadas
manualmente por SSH e sem histórico — ver Fase 3/6 do plano de deploy e
[ADR 0004](../docs/adr/0004-deploy-pull-via-systemd-timer.md) /
[ADR 0005](../docs/adr/0005-rolling-swap-sem-canario-blue-green.md) /
[ADR 0006](../docs/adr/0006-backup-pg-dump-s3.md).

## Importante: isto é uma reconstrução, não um espelho automático

Estes arquivos foram escritos a partir do comportamento documentado (plano de deploy + ADRs +
case study), não copiados byte a byte do servidor — não há pipeline puxando o estado real da EC2
pra cá. Antes de confiar neles como fonte da verdade, faça um diff manual contra o que está
rodando:

```
ssh -i ~/.ssh/estado-key.pem ec2-user@54.94.231.248
diff ~/estado/docker-compose.yml -   # cole o conteúdo daqui, ou use scp pra trazer os arquivos reais
```

Ajuste o que divergir. Depois desse primeiro alinhamento, trate este diretório como fonte da
verdade e replique manualmente pro servidor a cada mudança (deploy dessas configs continua sendo
manual, deliberadamente — ver ADR 0004: não existe canal automatizado de push pra dentro da EC2).

## Layout

| Neste repo | Na instância | O que é |
|---|---|---|
| `deploy/estado/docker-compose.yml` | `~/estado/docker-compose.yml` | Só o Postgres (o container da app não é mais gerenciado pelo Compose, ver ADR 0005) |
| `deploy/estado/deploy.sh` | `~/estado/deploy.sh` | Rolling swap: sobe a imagem nova, espera `/actuator/health`, só então troca |
| `deploy/estado/backup.sh` | `~/estado/backup.sh` | `pg_dump` diário comprimido, valida tamanho mínimo, envia pro S3 |
| `deploy/estado/.env.example` | `~/estado/.env` (real, `chmod 600`) | Template — segredo real nunca vai pro repo, fica só na instância + Bitwarden |
| `deploy/proxy/docker-compose.yml` | `~/proxy/docker-compose.yml` | Caddy, stack compartilhada entre apps do portfólio |
| `deploy/proxy/Caddyfile` | `~/proxy/Caddyfile` | Roteamento HTTPS, inclui o fix do prefixo `/api` |
| `deploy/systemd/*.service`, `*.timer` | `/etc/systemd/system/` | Timers de deploy (5 min) e backup (diário 06:00 UTC) |

## Aplicar uma mudança na instância

```
scp -i ~/.ssh/estado-key.pem deploy/estado/docker-compose.yml deploy/estado/deploy.sh deploy/estado/backup.sh ec2-user@54.94.231.248:~/estado/
scp -i ~/.ssh/estado-key.pem deploy/proxy/docker-compose.yml deploy/proxy/Caddyfile ec2-user@54.94.231.248:~/proxy/

scp -i ~/.ssh/estado-key.pem deploy/systemd/*.service deploy/systemd/*.timer ec2-user@54.94.231.248:/tmp/
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
