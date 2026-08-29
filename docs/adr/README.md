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
- [0007 — EC2 Auto Recovery via alarme do CloudWatch](0007-ec2-auto-recovery.md)
- [0008 — Rollback manual via tag registrada](0008-rollback-manual-por-tag-registrada.md)
- [0009 — Prune semanal de imagens Docker dangling](0009-prune-semanal-de-imagens-dangling.md)
- [0010 — Criptografar o volume raiz EBS](0010-encriptar-volume-raiz-ebs.md)
- [0011 — Terraform via import, sem Terragrunt](0011-terraform-import-sem-terragrunt.md)
- [0012 — Observabilidade via Grafana Cloud + Alloy, push direto sem custo AWS](0012-grafana-cloud-alloy-observabilidade.md)
- [0013 — Frontend Angular em S3 + CloudFront, saindo do Spring Boot](0013-frontend-s3-cloudfront.md)
- [0014 — Liquibase em vez de hibernate.ddl-auto:update](0014-liquibase-em-vez-de-ddl-auto.md)
- [0015 — Backend S3 pro Terraform + drift-check semanal via GitHub Actions](0015-terraform-backend-s3-drift-check-ci.md)
