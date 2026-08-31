# ADR 0016: Rate limiting por IP, em memoria, sem autenticacao

## Status
Aceito

## Contexto
A API nao tem autenticacao nenhuma - qualquer um pode criar, alterar ou excluir estados via
`/estado/**`, sem identificacao de quem esta chamando. Isso e aceitavel para o dado em si (unidades
federativas do Brasil, sem PII nem valor sensivel), mas deixa a API exposta a abuso trivial: um
laco de retry descontrolado, um scraping simples, ou so trafego malicioso gratuito, sem nenhum
custo pro atacante nem limite hoje.

Adicionar autenticacao de verdade resolveria isso de forma mais completa, mas e um escopo bem
maior (usuario, sessao/token, o que autenticar e por que) desproporcional a um CRUD de portfolio
sem dado sensivel. Rate limiting por IP e a mitigacao de menor custo que cobre o caso concreto -
nao substitui autenticacao, complementa.

## Decisao
- [Bucket4j](https://github.com/bucket4j/bucket4j) (`bucket4j-core`, biblioteca Java pura, sem
  dependencia de Redis/store externo) - token bucket em memoria, um por IP
  (`ConcurrentHashMap<String, Bucket>` em `RateLimitFilter`).
- Limite default: 60 requisicoes/minuto por IP, configuravel via
  `RATE_LIMIT_CAPACIDADE`/`RATE_LIMIT_JANELA_SEGUNDOS` (mesmo padrao de override por env var do
  `API_ORIGIN_PERMITIDA`). Valor generoso o bastante pra nao incomodar uso normal (inclusive
  testes manuais em rajada), baixo o bastante pra travar abuso simples.
- Excedente devolve `429 Too Many Requests` com o mesmo formato `ErrorResponseDto` (mensagem +
  `requestId`) do resto da API, escrito direto no filtro - nao passa pelo
  `CustomGlobalExceptionHandler` porque roda antes do dispatch pro controller.
- Filtro roda logo depois do `RequestIdFilter` (`Ordered.HIGHEST_PRECEDENCE + 1`), pra ja ter
  `requestId` no MDC ao rejeitar, mas antes de qualquer outro processamento.
- **Achado durante a implementacao**: `request.getRemoteAddr()` atras do Caddy sempre resolveria
  pro IP do proprio Caddy (mesmo IP interno pra todo cliente), nao o IP real - o que tornaria o
  limite global em vez de por cliente. Corrigido habilitando
  `server.forward-headers-strategy: framework`, que faz o Spring resolver `getRemoteAddr()` a
  partir do `X-Forwarded-For` que o Caddy ja injeta. Seguro confiar nesse header aqui porque o
  container `estado-app` so e alcancavel via Caddy - a porta 8080 nunca e publicada pro host (ver
  ADR 0012), entao ninguem consegue falar direto com a app pra forjar o header.

## Alternativas consideradas
- **Nao fazer nada / esperar por autenticacao**: autenticacao e a correcao de raiz do problema mais
  amplo (API publica sem identidade), mas rate limiting e ortogonal - mesmo com auth, um usuario
  autenticado ainda pode abusar da API. Nao faz sentido esperar um escopo maior pra mitigar um
  problema menor e resolvivel agora.
- **`nestjs-rate-limiter`/bibliotecas de terceiro-partido especificas de outro framework**: nao se
  aplica aqui (Spring, nao Nest), mas serve de leitura de contexto: um projeto irmao deste
  portfolio (`nest-order-api`) tem uma trava de dependabot justamente porque a lib de rate limit
  usada la ficou obsoleta e incompativel com o major novo do framework - reforca a escolha aqui por
  uma lib pequena, madura e sem acoplamento a versao do Spring.
- **Redis/store distribuido**: faria sentido com mais de uma instancia da API atras de um load
  balancer - nao e o caso aqui (uma unica EC2, ver ADR 0002/0007). In-memory e proporcional e
  reversivel (trocar por Redis depois e so trocar a implementacao do `Bucket`, sem mudar a
  interface do filtro).
- **Rate limiting no Caddy** (`rate_limit` do modulo `caddy-ratelimit`, plugin de terceiro) ou no
  CloudFront/WAF: mais proximo da borda (rejeita antes de chegar na app, mais barato por
  requisicao), mas exigiria build customizado do Caddy (o modulo nao vem na imagem oficial) ou
  configurar AWS WAF (custo mensal, mais um recurso Terraform). Descartado pelo mesmo raciocinio de
  proporcionalidade das ADRs 0002/0011: resolve o mesmo problema com mais infra.

## Consequencias
- Positivo: fecha a lacuna de abuso trivial numa API publica sem autenticacao, sem exigir
  autenticacao nem infra nova.
- Positivo: zero custo incremental - `bucket4j-core` e biblioteca pura, sem chamada de rede; volume
  extra de log (requisicoes rejeitadas) e desprezivel contra a cota do Grafana Cloud (ver ADR 0012).
- Negativo aceito: limite por IP, nao por cliente/usuario - varios clientes atras do mesmo NAT
  (rede corporativa, 4G compartilhado) dividem o mesmo bucket. Aceitavel pro volume de trafego
  desse projeto (uso pessoal/demonstracao); numa aplicacao real com trafego residencial em escala,
  seria motivo pra revisar.
- Negativo aceito: buckets em memoria nao sobrevivem a um restart/rolling swap do container - um
  IP que estava perto do limite ganha uma janela nova a cada deploy. Nao e um problema de
  seguranca (o limite volta a valer normalmente), so uma folga ocasional sem consequencia real na
  escala desse projeto.
- Negativo aceito: sem alerta dedicado no Grafana Cloud pra taxa de 429 (diferente do 5xx, ver ADR
  0012) - um pico de rate limiting hoje so aparece se alguem for olhar o dashboard/log
  manualmente. Registrado aqui como proximo passo, nao como esquecimento; mesma dosagem de
  observabilidade do resto do projeto (alertar so no que ja e sinal validado como necessario).
