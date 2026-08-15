# Architecture Decision Records

Registro das decisões de arquitetura tomadas na migração e no deploy deste
projeto — contexto, o que foi decidido, alternativas descartadas e
consequências (incluindo as que ficaram conscientemente sem mitigação).

- [0001 — Migração direta para Java 25 / Spring Boot 4.1](0001-migracao-direta-java25-springboot4.md)
- [0002 — EC2 + Docker Compose, não EC2 + RDS](0002-ec2-compose-vs-ec2-rds.md)
- [0003 — sslip.io + Caddy, não domínio próprio](0003-sslip-io-vs-dominio-proprio.md)
- [0004 — Deploy via systemd timer (pull), não GitHub Actions (push)](0004-deploy-pull-via-systemd-timer.md)
- [0005 — Rolling swap simples, não canário/blue-green](0005-rolling-swap-sem-canario-blue-green.md)
- [0006 — Backup diário via pg_dump + S3, não WAL archiving nem RDS](0006-backup-pg-dump-s3.md)
