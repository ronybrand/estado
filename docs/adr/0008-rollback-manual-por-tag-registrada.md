# ADR 0008: Rollback manual via tag registrada, não pipeline de rollback automático

## Status
Aceito

## Contexto
O rolling swap ([ADR 0005](0005-rolling-swap-sem-canario-blue-green.md)) protege contra uma
imagem que não sobe — falha no health check, container antigo continua no ar. Ele não protege
contra uma imagem que sobe saudável mas tem um bug funcional (regressão de lógica, resposta
errada, migração de schema que corrompe dado): `/actuator/health` reflete só "processo de pé,
conectado no Postgres", não corretude de negócio. Nesse caso, reverter significava outro commit
+ esperar o ciclo inteiro de novo (CI, publish, até 5min do timer) — o mesmo material pra um
rollback rápido (tags por sha já publicadas no GHCR a cada push) já existia, só não havia processo
nem lugar registrado apontando qual delas era a última boa.

## Decisão
`deploy.sh` passa a gravar, a cada troca bem-sucedida, a tag da versão que **estava rodando antes**
(presumivelmente boa — ela mesma já passou por este health check no deploy anterior) num arquivo
`last-good-tag`. Um script novo, `rollback.sh`, lê essa tag (ou aceita uma tag/sha explícita como
argumento, pra voltar mais de uma versão) e reaplica o mesmo swap com health check — se a tag de
rollback também não ficar saudável, o container atual não é tocado.

A tag gravada é o sha completo do commit, lido do label OCI `org.opencontainers.image.revision` que
o `docker/metadata-action` já grava automaticamente na imagem. Isso exigiu alinhar
`docker-publish.yml` pra publicar a tag por sha também em formato completo
(`type=sha,prefix=,format=long`) — antes era o sha curto (7 chars), que não batia como string com o
valor do label, tornando a tag registrada impossível de puxar do GHCR.

A lógica de swap foi extraída pra `lib-swap.sh`, sourced tanto por `deploy.sh` quanto por
`rollback.sh` — duplicar o loop de health check entre os dois script arriscava os dois divergirem
com o tempo.

## Alternativas consideradas
- **Manter as últimas N tags anotadas** (histórico completo, não só a imediatamente anterior):
  cobriria o caso de duas versões seguidas ruins. Descartado por desproporcional nesse estágio — as
  tags por sha já existem no GHCR pra sempre (não expiram), então voltar mais de um passo já é
  possível hoje passando a tag manualmente pro `rollback.sh`, só não fica pré-listado.
- **Pipeline de rollback automático** (ex: reverter sozinho se um alarme/métrica de erro disparar
  logo após um deploy): resolveria sem intervenção humana, mas exige definir e manter um sinal de
  "essa versão está ruim" além do health check (taxa de erro, métrica de negócio) — infraestrutura
  de observabilidade que este projeto não tem e não se justifica só por isso.
- **Aceitar como risco em aberto**, documentado como o `⚠️` de falha de AZ no case study: era a
  opção de menor esforço. Descartada porque o custo de fechar era baixo (reaproveita o swap que já
  existe, ~40 linhas somando os dois scripts) contra um ganho real — reduz o tempo de reação de "um
  ciclo de commit+CI+publish+timer" pra "um comando".

## Consequências
- Positivo: rollback de uma versão saudável-mas-quebrada agora é `./rollback.sh` (segundos), não um
  novo commit esperando o pipeline inteiro.
- Positivo: reaproveita a mesma proteção do health check — uma tag de rollback que também falha não
  piora a situação.
- Negativo aceito: só a versão imediatamente anterior é gravada automaticamente. Voltar mais de um
  passo exige saber a tag/sha de memória ou consultar o GHCR manualmente
  (`gh api /users/ronybrand/packages/container/estado/versions`) e passar pro `rollback.sh`
  explicitamente.
- Negativo aceito, herdado do ADR 0005: isso não detecta sozinho que uma versão está ruim — alguém
  (ou algum monitoramento que este projeto não tem) precisa perceber o bug e decidir rodar o
  rollback. Não é um mecanismo de auto-recuperação, é um atalho pra quando o problema já foi
  identificado.
