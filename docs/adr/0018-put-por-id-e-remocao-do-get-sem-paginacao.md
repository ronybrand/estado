# ADR 0018: PUT /estado/{id}, remocao do GET /estado sem paginacao

## Status
Aceito

## Contexto
`PUT /estado` identificava o recurso a atualizar pelo `id` dentro do corpo da requisicao, em vez
da URL - uma inconsistencia com a convencao REST usual (`GET /estado/{id}`, `DELETE /estado/{id}`
ja seguiam o padrao). Isso tambem exigia validacao extra so pra cobrir `id` ausente/nulo no corpo
(`EstadoUpdateRequestDTO`), redundante com o que um path variable ja garante.

Paralelamente, a ADR anterior de paginacao (`GET /estado/paginado`, ver commit da feature) manteve
o `GET /estado` original (retorna a lista inteira, sem `Pageable`) por compatibilidade na epoca.
Com o endpoint paginado maduro e testado, manter os dois listagem-endpoints so perpetua a mesma
falta de limite de payload que a paginacao foi criada pra resolver.

## Decisao
- `PUT /estado` virou `PUT /estado/{id}` - o id vem exclusivamente do path (validado com o mesmo
  `@Positive @Max` ja usado em `GET /estado/{id}` e `DELETE /estado/{id}`, consistente entre os tres
  endpoints que operam sobre um recurso especifico). `EstadoUpdateRequestDTO` perdeu o campo `id`;
  o corpo agora so carrega `nome`/`sigla`, o que tambem simplificou a validacao (nao precisa mais
  de `@NotNull` em id nem do teste que cobria essa ausencia).
- `GET /estado` (sem paginacao, retornava `List<EstadoDTO>` completo) foi removido. `GET
  /estado/paginado` passa a ser o unico endpoint de listagem. `EstadoService.listar()` tambem foi
  removido por ficar sem uso.
- Sem endpoint de compatibilidade/redirect para o `GET /estado` antigo: os dois frontends
  conhecidos (`angular_estado`, `react_estado`) sao atualizados na mesma janela de mudanca (ver PRs
  correspondentes), entao nao ha cliente externo desconhecido a proteger.

## Alternativas consideradas
- **Manter `id` no corpo do PUT, so adicionar validacao de que bate com o path**: rejeitada -
  adiciona uma regra de consistencia (o que fazer se divergir? 400? ignorar um dos dois?) sem
  ganho real sobre simplesmente nao aceitar `id` no corpo.
- **Manter `GET /estado` como alias do `/paginado` com pagina default grande**: rejeitada -
  continuaria sem limite real (cliente podia pedir `size` grande via query string do alias do
  mesmo jeito que já podia), so adicionaria uma rota a mais pra manter e documentar.
- **Limitar numero de pagina (`page`) e quantidade/tamanho de campos em `sort` no `/paginado`**,
  levantado em code review externo como potencial vetor de "DoS via OFFSET grande" ou "ORDER BY
  custoso": rejeitado por desproporcional a este dominio - a tabela e as 27 unidades federativas do
  Brasil, fixa por natureza (nao ha fluxo de escrita em massa nem crescimento esperado). Um `OFFSET`
  grande contra 27 linhas nao tem custo mensuravel, e limitar `page`/`sort` de forma arbitraria
  (sem relacao com o tamanho real dos dados) adicionaria complexidade sem mitigar um risco que
  existe de fato neste projeto - mesmo raciocinio de proporcionalidade ja registrado nas ADRs
  0016/0017. Revisitar se o dominio deste endpoint mudar (ex: reaproveitar o mesmo controller para
  uma entidade que cresce).

## Consequencias
- Positivo: os quatro endpoints de mutacao/busca por recurso (`GET/PUT/DELETE /estado/{id}`) agora
  seguem a mesma convencao de identificar o recurso pela URL - contrato mais previsivel.
- Positivo: um unico endpoint de listagem (`/paginado`), sem duas rotas fazendo a mesma coisa com
  garantias diferentes.
- Negativo aceito (breaking change): qualquer cliente que ainda chame `PUT /estado` (corpo com id)
  ou `GET /estado` (lista completa) quebra. Mitigado coordenando a mudanca com os dois frontends
  conhecidos na mesma janela; nao ha SLA de compatibilidade retroativa documentado para esta API de
  portfolio.
