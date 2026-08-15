# ADR 0001: Migração direta para Java 25 / Spring Boot 4.1, sem passos intermediários

## Status
Aceito

## Contexto
O projeto estava parado em Java 8 e Spring Boot 2.0.5.RELEASE (2018). O caminho
oficialmente recomendado pela Spring pra saltos grandes de versão é incremental —
2.0 → 2.7 → 3.0 → 3.x → 4.0 → 4.1 — validando a cada passo, porque cada major
version costuma trazer breaking changes específicas (remoção de `javax.*` em favor
de `jakarta.*`, mudanças de baseline do Hibernate, etc).

## Decisão
Pular direto para Spring Boot 4.1.0 / Java 25, corrigindo os erros de compilação
e comportamento que apareceram, em vez de atravessar cada versão major
intermediária.

## Alternativas consideradas
- **Caminho incremental completo** (2.0→2.7→3.0→3.x→4.0→4.1): mais seguro pra
  aplicações grandes com muitas dependências e módulos complexos (Security,
  Batch, Cloud), onde builds quebrados em cada etapa ajudam a isolar a causa.
  Descartado porque o custo (tempo, número de PRs, número de vezes que o CI
  precisa rodar) não se paga num projeto deste tamanho.

## Consequências
- Positivo: uma única leva de mudanças, mais rápido de revisar e concluir.
- Negativo: quando algo quebra, pode ser difícil saber "em qual das várias
  versões que pulamos isso mudou" — mitigado fazendo a validação bem granular
  em cada fase (compilar, rodar testes, testar manualmente cada endpoint) em vez
  de só confiar que "se compilou, está certo". Esse cuidado acabou sendo
  necessário mesmo: bugs de comportamento (trailing slash, CORS) só apareceram
  depois do deploy real, não durante a migração em si — ver
  [0005](0005-rolling-swap-sem-canario-blue-green.md) pelo contexto de como
  foram detectados e corrigidos em produção.
- Este projeto tinha poucas dependências e nenhum módulo Spring "pesado"
  (sem Security, sem Batch, sem Cloud), o que tornou o risco do salto direto
  administrável — essa decisão não generaliza pra qualquer projeto.
