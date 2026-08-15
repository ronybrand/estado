# ADR 0003: sslip.io + Caddy para HTTPS, em vez de domínio próprio

## Status
Aceito

## Contexto
A aplicação precisava de um endereço público com HTTPS real (não autoassinado)
pra ser acessível de forma confiável, incluindo pelo próprio front-end Angular
(que roda no navegador e não aceita chamadas a um backend sem certificado
válido sem fricção extra).

## Decisão
Usar `<elastic-ip>.sslip.io` — um serviço de DNS wildcard gratuito que resolve
qualquer subdomínio contendo um IP pro próprio IP — combinado com Caddy como
proxy reverso, que obtém e renova certificados Let's Encrypt automaticamente
via desafio HTTP.

## Alternativas consideradas
- **Domínio próprio via Route 53**: mais profissional, permite trocar de IP
  sem quebrar links, mais fácil de lembrar. Descartado (por ora) porque tem
  custo recorrente (~US$12-15/ano) e não era necessário pra validar o
  deploy — dá pra trocar depois só editando o Caddyfile, sem mudar nada na
  aplicação.
- **Certificado autoassinado**: gratuito e imediato, mas gera aviso de
  segurança no navegador e pode bloquear chamadas `fetch`/CORS do front-end
  dependendo da configuração — não serve pra uma demo que deve funcionar sem
  fricção pra quem for avaliar o portfólio.

## Consequências
- Positivo: HTTPS real, funcionando, sem custo e sem esperar propagação de DNS
  de um domínio comprado.
- Negativo: URL pouco memorável (`54.94.231.248.sslip.io`) — inadequado pra
  divulgação séria do projeto a médio prazo; domínio próprio é o upgrade
  natural quando isso importar.
- Reversível a baixo custo: a troca de domínio não exige mudança na
  aplicação nem no `docker-compose.yml` — só no `Caddyfile`.
