# ADR 0013: Frontend Angular em S3 + CloudFront, saindo do Spring Boot

## Status
Aceito

## Contexto
O build do Angular (`angular_estado`) vinha sendo commitado manualmente em
`src/main/resources/static/` e servido pelo próprio Spring Boot, junto na
mesma imagem Docker do backend. Isso acopla o deploy do front ao do back (um
release do Angular exige rebuild/republish do backend) e não usa CDN - todo
asset estático é servido pela EC2 única do portfolio.

A ADR 0003 já registrava domínio próprio como "o upgrade natural" quando isso
importasse; separar o front em hosting estático dedicado é o mesmo tipo de
evolução, mas não depende de ter um domínio - dá pra fazer com o domínio
default do CloudFront (`*.cloudfront.net`) e trocar por domínio próprio depois
sem mexer no bucket nem no app (só `aliases` + certificado ACM na
distribution).

## Decisão
- Bucket S3 privado (Block Public Access total, sem website hosting) +
  CloudFront com Origin Access Control - o bucket nunca é acessível direto,
  só via CDN.
- A mesma distribution CloudFront tem dois origins: S3 pro `/*` (assets do
  Angular) e o backend atual (EC2 + Caddy, `<elastic-ip>.sslip.io`) pro
  `/api/*` - preserva a suposição de mesma-origem que `environment.prod.ts`
  já faz (`apiUrl: '/api'`), sem precisar reescrever a app nem lidar com
  cookies/CORS cross-origin de verdade.
- `custom_error_response` 403/404 → `/index.html` (200): obrigatório porque
  o Angular Router faz roteamento client-side: sem isso, refresh numa rota
  como `/estado/editar/5` quebra (S3 responde 403 pra um path que não é um
  objeto real).
- Deploy do front via GitHub Actions com OIDC (sem access key estática na
  conta) - a role só pode ser assumida a partir do branch `master` do repo
  do Angular, com permissão só pra sincronizar aquele bucket e invalidar
  aquela distribution.
- `src/main/resources/static/` deixa de ser usado - o backend volta a ser só
  API.

## Alternativas consideradas
- **Manter servindo pelo Spring Boot**: mais simples (zero infra nova), mas
  mantém o acoplamento de deploy e não ganha CDN. Descartado porque o ponto
  desta mudança é justamente desacoplar.
- **S3 website hosting público (sem CloudFront)**: mais simples e mais
  barato, mas sem HTTPS nativo do S3 website endpoint e sem CDN de verdade.
  Descartado por segurança (bucket público) e porque o Angular Router
  também precisaria de fallback pra `index.html`, que o S3 website hosting
  faz de forma mais limitada que o CloudFront.
- **Domínio próprio + ACM desde já**: mais "definitivo", mas exige comprar
  um domínio antes de poder aplicar o Terraform. Adiado pelo mesmo
  raciocínio da ADR 0003 - dá pra adicionar depois sem re-trabalho.

## Consequências
- Positivo: deploy do front independente do back (CI próprio, sem rebuild
  de imagem Docker); CDN reduz latência e carga na EC2; bucket nunca fica
  público.
- Positivo: reversível a baixo custo - trocar domínio depois é só
  `aliases`/`acm_certificate_arn` na distribution, sem mudar bucket, app ou
  IAM role.
- Negativo: mais uma peça de infra pra operar (CloudFront tem propagação de
  alguns minutos por deploy de configuração, e invalidação de cache tem
  custo/latência a cada release do front).
- Negativo temporário: `API_ORIGIN_PERMITIDA` (CORS no `WebConfig`) precisa
  ser atualizado pro domínio do CloudFront (`*.cloudfront.net`) depois do
  primeiro `terraform apply`, já que esse valor só existe depois de criada a
  distribution.
