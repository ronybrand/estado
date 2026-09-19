# ADR 0020: hibernate.ddl-auto=validate só em teste, não em produção

## Status
Aceito — ajusta parcialmente a [ADR 0014](0014-liquibase-em-vez-de-ddl-auto.md): a adoção do
Liquibase em vez de `ddl-auto:update` continua válida e não muda aqui; o que muda é só o valor de
`ddl-auto` em produção (`validate` → `none`).

## Contexto
A ADR 0014 colocou `hibernate.ddl-auto: validate` em `application.yml`, sem diferenciar por
profile — vale pra produção também. O Hibernate confere o mapeamento da entidade contra o schema
real no boot e derruba a aplicação se divergir.

Uma análise comparativa com o projeto irmão `spring-order-api` (que usa `ddl-auto: none` em
produção e só valida num teste dedicado, `SchemaValidationIT`) trouxe à tona um trade-off que a
ADR 0014 não discutiu: **onde** essa checagem deveria rodar. `validate` não é perigoso como
`update`/`create` (nunca altera schema, só confere), mas ainda é uma fonte conhecida de falso
positivo do Hibernate — divergência de naming strategy, mapeamento de tipo específico de banco,
índice que o Hibernate espera de um jeito e o Liquibase criou de outro. Rodando em produção, um
falso positivo desses derruba um deploy de verdade, na pior hora possível. Rodando só em teste, o
mesmo falso positivo custa um comentário de PR.

A proteção que a ADR 0014 queria (schema e entidade nunca divergem sem alguém perceber) já existe
sem depender do profile de produção: `EstadoRepositoryIT` já sobe um Postgres real via
Testcontainers com o Liquibase aplicado de ponta a ponta - só faltava rodar com `validate` também.

## Decisão
`hibernate.ddl-auto` volta a `none` no `application.yml` base (vale pra produção). Adicionado
`@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")` em
`EstadoRepositoryIT`, que já usa Testcontainers/Liquibase - sem precisar de uma classe de teste
nova nem de um container a mais.

## Alternativas consideradas
- **Manter como estava (validate em todo profile)**: mantém a rede de segurança em produção, mas
  aceita o risco de um falso positivo do Hibernate travar um deploy legítimo - o mesmo risco que a
  ADR 0016 (rate limiting) e a ADR 0017 (JWT) evitam conscientemente noutras áreas deste projeto ao
  preferir a opção de menor blast radius.
- **Um profile `test` dedicado só para isso**: mais explícito, mas o projeto não tem hoje um
  `application-test.yml` - criar um só pra essa propriedade seria mais código do que o
  `@TestPropertySource` direto na classe que já precisa disso.

## Consequências
- Positivo: um mapeamento incorreto de entidade ainda quebra o CI (mesma garantia de antes), mas
  nunca mais quebra um boot de produção por um falso positivo do Hibernate.
- Neutro: zero mudança de comportamento em produção fora da checagem em si - o Hibernate já não
  alterava schema (`validate` não faz `DDL`), só parava de conferir.
- Negativo aceito: se o Liquibase e as entidades divergirem de um jeito que só aparece com dados
  reais de produção (ex. uma coluna que existe mas com um tipo sutilmente incompatível só sob
  volume/encoding específico), o `EstadoRepositoryIT` pode não pegar - mesma classe de risco
  residual que `spring-order-api` já aceita com essa mesma escolha.
