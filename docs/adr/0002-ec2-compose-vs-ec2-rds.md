# ADR 0002: Instância EC2 única com Docker Compose, em vez de EC2 + RDS separados

## Status
Aceito

## Contexto
Precisava hospedar a aplicação (Spring Boot + PostgreSQL) na AWS, usando o
crédito de uma conta nova (até US$200, expira em 12 meses, drena contra uso
real — não é mais o modelo antigo de "EC2 grátis por 12 meses" separado). O
objetivo declarado também incluía deixar espaço pra hospedar mais alguns apps
de portfólio na mesma verba.

## Decisão
Uma única instância EC2 (`t3.small`) rodando Postgres em container Docker, ao
lado da aplicação — sem usar RDS (banco gerenciado da AWS).

## Alternativas consideradas
- **EC2 (app) + RDS (banco gerenciado)**: mais próximo de um setup de produção
  real — backups automáticos, patching gerenciado, Multi-AZ disponível.
  Descartado porque RDS `db.t3.micro` sozinho custa perto do que a EC2 inteira
  custa (~US$12-15/mês só o banco), o que dobraria o consumo do crédito sem
  trazer benefício real pra uma app pessoal sem exigência de alta
  disponibilidade.

## Consequências
- Positivo: crédito estica por mais tempo; uma instância só, mais simples de
  gerenciar; espaço pra hospedar mais apps de portfólio na mesma verba.
- ~~Negativo real, sem mitigação ainda: **não há backup do banco**~~ — fechado,
  ver [ADR 0006](0006-backup-pg-dump-s3.md).
- Negativo aceito conscientemente: instância única, zona de disponibilidade
  única, sem failover automático de infraestrutura. Um load balancer + segunda
  instância + Multi-AZ resolveria isso, mas custaria mais por mês do que a
  instância atual inteira, e é complexidade desproporcional pro estágio deste
  projeto.
