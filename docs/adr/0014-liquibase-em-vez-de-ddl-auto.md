# ADR 0014: Liquibase em vez de hibernate.ddl-auto:update

## Status
Aceito

## Contexto
O schema do banco era gerenciado pelo Hibernate (`ddl-auto: update`) - a tabela
`estado` era criada/alterada implicitamente a partir do mapeamento da entidade,
sem histórico versionado de migrations. Existia também um `schema.sql` na raiz
de `src/main/resources/`, mas com sintaxe MySQL (`AUTO_INCREMENT`) inválida
pra Postgres - nunca executado de fato, porque o Spring Boot só roda
`schema.sql` automaticamente contra datasource embarcado (H2 etc.), a menos
que `spring.sql.init.mode: always` seja setado explicitamente, o que nunca foi
o caso aqui. Um arquivo morto, com sintaxe errada, sobrevivendo no repo sem
nunca ter sido executado contra o Postgres real do projeto.

`ddl-auto: update` também é um risco conhecido em produção: o Hibernate decide
sozinho como alterar o schema a partir do diff do mapeamento, sem revisão
possível antes de aplicar, e sem forma de reverter uma alteração indesejada.

## Decisão
Adotado Liquibase (`spring-boot-starter-liquibase`), com changesets SQL em
`src/main/resources/db/changelog/`. `hibernate.ddl-auto` passa de `update`
pra `validate` - o Hibernate confere que o mapeamento da entidade bate com o
schema aplicado no startup (falha cedo se divergir), mas não altera mais nada
sozinho.

O desafio específico deste projeto: a tabela `estado` já existe em produção,
criada historicamente pelo `ddl-auto:update`. Um changeset comum de
`CREATE TABLE estado` falharia com "relation already exists" no primeiro
deploy. Resolvido com uma precondition SQL no changeset
(`--preConditions onFail:MARK_RAN` + `--precondition-sql-check` contando
linhas em `information_schema.tables`): se a tabela já existir, o Liquibase
marca o changeset como aplicado sem executar o `CREATE TABLE`, sem precisar de
nenhum passo manual (`changelogSync` ou similar) na EC2 antes do deploy.

Validado localmente contra Postgres real (Testcontainers, e também um
container manual simulando o estado de produção): banco novo cria a tabela
normalmente; banco com a tabela pré-existente só marca o changeset como
executado, aplicação sobe normal, `hibernate.ddl-auto:validate` confirma que o
schema batia com o mapeamento nos dois casos.

## Alternativas consideradas
- **Manter ddl-auto:update**: zero esforço, mas sem histórico de mudanças de
  schema revisável, sem rollback, e o Hibernate decidindo sozinho o que alterar
  em produção - o tipo de coisa que já causou incidente em times reais.
  Descartado por ser justamente o anti-padrão mais citado sobre Hibernate em
  produção.
- **Flyway em vez de Liquibase**: equivalente em maturidade pra esse caso de
  uso simples. Liquibase escolhido pelo formato SQL puro dos changesets
  (`--liquibase formatted sql`), que fica mais proximo do estilo do resto do
  projeto do que a convencao de nomenclatura de arquivo do Flyway.
- **changelogSync manual documentado**: mais simples de ler no changeset (SQL
  puro, sem precondition), mas exige lembrar de rodar um comando na EC2 antes
  do primeiro deploy com Liquibase - risco real de esquecer e o deploy falhar
  com "table already exists" bem na hora errada. Descartado a favor da
  precondition auto-guardada, que remove esse passo manual inteiramente.

## Consequências
- Positivo: mudanças de schema agora são commits revisáveis (changesets SQL
  com `--rollback`), não inferência implícita do Hibernate.
- Positivo: `schema.sql` morto/quebrado removido - não sobra código nunca
  executado com sintaxe errada confundindo quem lê o repo.
- Positivo: introdução em produção não exige passo manual, graças à
  precondition - o mesmo changelog funciona tanto pra um banco novo (Testcontainers,
  ambiente local do zero) quanto pro banco de produção já existente.
- Negativo aceito: uma camada a mais de configuração (changelog XML + SQL)
  pra um projeto desse porte - justificado pelo ganho de rastreabilidade e
  pela remoção do risco de alteração implícita de schema em produção.
