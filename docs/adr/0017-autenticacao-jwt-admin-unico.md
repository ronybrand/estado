# ADR 0017: Autenticacao JWT, usuario admin unico, via Spring Security

## Status
Aceito

## Contexto
A ADR 0016 registrou conscientemente a ausencia de autenticacao como escopo desproporcional na
epoca, usando rate limiting por IP como mitigacao menor - qualquer um ainda podia criar, alterar
ou excluir estados via `/estado/**`, sem identificacao de quem chama. Throttling reduz abuso
trivial, mas nao fecha o problema de fundo: a API de mutacao continua publica. Chegou a hora de
resolver isso de verdade, mantendo a mesma proporcionalidade que o resto do projeto ja demonstra
(operador unico, sem infra desnecessaria - ver ADRs 0002/0007/0011): um unico usuario admin,
configurado via env var, sem tabela de usuarios nem roles.

## Decisao
- Usuario admin unico hardcoded via env var (`ADMIN_USERNAME`/`ADMIN_PASSWORD_HASH`, este ultimo
  ja em hash BCrypt) - sem tabela de usuarios, sem cadastro, sem "esqueci minha senha".
- JWT self-issued pelo proprio backend via `POST /auth/login`, assinado com HMAC (HS256, segredo
  em `JWT_SECRET`), sem refresh token - expiracao curta (default 60min, configuravel) e re-login
  manual sao aceitaveis pra um unico admin operando manualmente.
- `spring-boot-starter-security` adotado (nao um filtro artesanal equivalente ao `RateLimitFilter`
  ja existente) especificamente porque autenticacao e o tipo de funcionalidade que um portfolio
  Java/Spring precisa demonstrar usando a ferramenta padrao do ecossistema - ao contrario do rate
  limiting, onde `bucket4j` fez sentido por ser leve e nao ter uma "opcao Spring Security"
  comparavel. Uso minimo: so `SecurityFilterChain` + `JwtAuthFilter` (um `OncePerRequestFilter`
  customizado, no mesmo estilo do `RequestIdFilter`/`RateLimitFilter`), sem `UserDetailsService`,
  sem form login, sem sessao (`STATELESS`). Nao adiciona infra nova (sem tabela, sem store de
  sessao) - so uma dependencia e uma classe de config, e ganha `BCryptPasswordEncoder` mantido e
  semantica 401/403 padrao do framework.
- Escopo de protecao: so `POST`/`PUT`/`DELETE` em `/estado/**` exigem token; `GET` continua
  publico, mesmo raciocinio da ADR 0016 (dado nao sensivel - unidades federativas do Brasil).
- Corpo de erro do 401 no mesmo formato `ErrorResponseDto` do resto da API (via
  `AuthenticationEntryPoint` customizado, escrevendo JSON direto - roda fora do
  `DispatcherServlet`/`@RestControllerAdvice`, mesma tecnica ja usada no `RateLimitFilter`).
- CORS continua tratado pelo `WebMvcConfigurer` ja existente em `WebConfig` - o Security so
  delega pra ele (`.cors(Customizer.withDefaults())`), sem duplicar a config de origem permitida.

## Alternativas consideradas
- **Multi-usuario com tabela/roles**: desproporcional - o dominio deste CRUD nao tem conceito de
  "usuario" nenhum, so um operador. Adicionar isso seria complexidade sem necessidade real.
- **OAuth2/identity provider externo (Auth0, Keycloak)**: resolve um problema de federacao/
  multi-tenant que este projeto nao tem. Adicionaria infra e custo operacional (mais um servico
  pra manter ou assinatura paga) sem ganho proporcional pra um unico admin conhecido.
- **Chave assimetrica (RSA) em vez de HMAC simetrico**: sem beneficio aqui - o mesmo processo
  emite e valida o token, entao nao ha fronteira de confianca entre emissor e validador que
  justifique separar chave publica/privada. So mais complexidade de gerar e rotacionar duas
  chaves em vez de um segredo.
- **Refresh token**: mais um tipo de token, mais storage, mais logica de rotacao - desproporcional
  pra um unico usuario manual que pode simplesmente logar de novo quando o token expirar.
- **Cookie httpOnly em vez de localStorage no frontend**: mais resistente a XSS, mas exige lidar
  com CSRF (o cookie e enviado automaticamente pelo browser em toda requisicao, diferente de um
  header explicito) e encaixa pior num setup API+SPA puro sem sessao no backend. `localStorage`
  escolhido, com a exposicao a XSS aceita dado que nenhum dos dois frontends
  (`angular_estado`/`react_state`) renderiza HTML/conteudo de terceiro sem escapar.
- **Filtro artesanal equivalente ao Spring Security** (parsing/validacao manual de JWT, sem a
  dependencia): descartado - ver raciocinio na secao Decisao acima. Autenticacao e especificamente
  onde vale demonstrar a ferramenta padrao do mercado, nao economizar peso.

## Consequencias
- Positivo: fecha a lacuna real que a ADR 0016 deixou aberta conscientemente - mutacao na API
  agora exige identidade, nao so limite de taxa.
- Positivo: demonstra Spring Security de forma concreta no portfolio, sem trazer complexidade
  desproporcional (sem tabela de usuarios, sem sessao, sem infra nova).
- Negativo aceito: token em `localStorage` e legivel por qualquer script injetado (XSS) - aceito
  porque ambas as SPAs sao pequenas, com dependencias fixadas e sem renderizacao de conteudo de
  terceiro/gerado por usuario; expiracao curta do token limita o raio de dano.
- Negativo aceito: sem fluxo de rotacao/reset de senha - trocar a senha do admin significa gerar
  um hash novo e re-deployar, aceitavel pra uma credencial unica gerenciada manualmente.
- Negativo aceito: comprometimento do segredo HMAC (`JWT_SECRET` vazado, por exemplo) permite
  forjar token indefinidamente ate rotacionar - mesma classe de risco operacional ja aceita hoje
  pra `POSTGRES_PASSWORD` neste modelo de deploy (segredo em `.env` na instancia, nunca no repo).
- Negativo aceito: sem auditoria de login (quem logou quando) - proporcional a um unico admin
  conhecido; nao ha "quem" pra distinguir.
