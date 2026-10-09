# ADR 0024: Domínio próprio `.click` no Route 53, em lugar do sslip.io

## Status
Aceito. Substitui o [0003](0003-sslip-io-vs-dominio-proprio.md).

## Contexto
O ADR 0003 escolheu `<ip>.sslip.io` e apontava o domínio próprio como "o upgrade natural", dizendo que
bastaria editar o Caddyfile. O hostname ficou amarrado ao IP: trocar de IP, de região ou de arquitetura
muda a URL, e a troca atinge mais do que o Caddyfile (a origem do CloudFront vem do IP no Terraform, o
`lib-swap.sh` tem a URL do GitHub Deployments e a CSP do frontend citava um subdomínio).

## Decisão
Registrar `ronybrand.click` no Route 53 e servir o backend em `api.ronybrand.click`.

- **`.click`**: US$ 3 por ano (registro e renovação), o mais barato dos TLDs do Route 53. A zona hospedada
  custa US$ 0,50 por mês, o que dá ~US$ 9 por ano no total, contra ~US$ 22 de um `.com` na mesma
  conta e ~US$ 9 a 12 de um `.com` na Cloudflare.
- **Domínio de marca com subdomínios por app** (`api.`, e outros depois), já que a instância foi pensada
  para hospedar mais apps do portfólio.
- **A zona do Route 53 é lida como data source** no Terraform (`terraform/dns.tf`), e não importada nem
  recriada: ela nasce no registro, com os nameservers já apontados, e o Terraform só gerencia o registro
  `A` (TTL 300, apontando para o Elastic IP). Assim ele não consegue apagar a zona.
- **Transição em fases**: o Caddy passou a atender os dois nomes, o CloudFront foi trocado para o novo
  e o `sslip.io` sai do Caddy só depois de alguns dias estável.
- O CORS não muda: as origens permitidas são as dos frontends (CloudFront e Vercel), não o hostname da API.

## Alternativas consideradas
- **Seguir no sslip.io**: grátis, mas o hostname muda com o IP e é inadequado para divulgar o projeto.
- **Domínio `.com` na Cloudflare com DNS grátis**: ~US$ 9 por ano, parecido, mas o registro e o DNS
  ficam fora da conta que já tem o resto da infraestrutura e do Terraform.
- **Domínio `.br` no Registro.br** (R$ 40 por ano): mais caro que o `.click`.

## Consequências
- Positivo: trocar de IP, instância, região ou arquitetura vira uma edição de DNS (TTL baixo). O
  certificado continua emitido sozinho pelo Caddy (Let's Encrypt).
- Negativo aceito: ~US$ 9 por ano de custo recorrente. O domínio precisa ser renovado (renovação
  automática ligada) e o contato precisa ter o e-mail verificado.
- O React na Vercel usa a variável `BACKEND_API_URL`, fora do repositório: precisa apontar para o nome
  novo antes de o `sslip.io` ser removido do Caddy.
