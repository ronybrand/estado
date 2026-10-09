# Plano: domínio próprio (`.click` na AWS) e, opcionalmente, recriar a instância com disco menor

Status em 2026-10-09: domínio **`ronybrand.click` registrado pela CLI** (1 ano, US$ 3, renovação
automática e proteção de WHOIS ligadas; operação `a354caf6-bce7-49c3-a8fd-0935030841f1`) e zona
hospedada `Z1023450XBS9WJ1974JR` criada pelo registro. Fases 0 e 1 em andamento; as demais ainda não
foram executadas. O escopo (fase 5) segue em aberto.

Progresso: fases 0, 1 e 2 concluídas (DNS aplicado, Caddy com certificado do Let's Encrypt para
`api.ronybrand.click`); fase 3 em PR, com o `terraform apply` previsto para depois do merge.

## 1. Por que, e o que o ADR 0003 já dizia

O [ADR 0003](adr/0003-sslip-io-vs-dominio-proprio.md) escolheu `<ip>.sslip.io` e já apontava o domínio
próprio como "o upgrade natural", dizendo que bastaria editar o Caddyfile. Na prática o hostname aparece
em mais lugares (seção 4, fase 3), e hoje ele está amarrado ao IP: trocar de IP, região ou arquitetura
muda a URL. Um domínio próprio desacopla as duas coisas e torna qualquer migração futura uma edição de DNS.

Este plano resolve o hostname primeiro (baixo risco, sem janela fora do ar) e deixa a recriação da
máquina como etapa opcional, que só então fica barata.

## 2. Decisões em aberto

| # | Decisão | Opções / observação |
|---|---|---|
| 1 | Nome do domínio | Disponíveis em 2026-10-09: `ronybrand.click`, `rony-brand.click`, `estado-ronybrand.click`, `estados-do-brasil.click`, `brazilian-states.click`, `ronybrand-portfolio.click`. Sugestão: `ronybrand.click`, com subdomínios por app (`api.`, `estado.`, ...), já que a instância foi pensada para hospedar mais apps. |
| 2 | Quem registra | O registro pede dados de contato (nome, endereço, telefone, e-mail). Registrar no console (Route 53, *Registered domains*) com *Privacy protection* ligada. |
| 3 | Escopo | (a) só domínio e troca de hostname; (b) também recriar a instância com disco de 16 GB em `sa-east-1`; (c) idem em `us-east-1` e/ou ARM (`t4g.micro`). |

## 3. Custo (preços da API da AWS em 2026-10-09)

| Item | Valor |
|---|---|
| Domínio `.click` | US$ 3 por ano, cobrado adiantado e renovado por ano inteiro (sem rateio) |
| Zona hospedada Route 53 | US$ 0,50 por mês (tabela pública), mais US$ 0,40 por milhão de consultas |
| **Total** | **~US$ 9 por ano** |

Opcional, na recriação da máquina (preço por hora, 730 h/mês):

| | `sa-east-1` | `us-east-1` |
|---|---|---|
| `t3.micro` | US$ 12,26 | US$ 7,59 |
| `t4g.micro` (ARM) | US$ 9,78 | US$ 6,13 |
| Disco gp3 de 30 GB / de 16 GB | US$ 4,56 / US$ 2,43 | US$ 2,40 / US$ 1,28 |

A mudança de região sozinha economiza uns US$ 80 a 100 por ano; descontado o domínio, uns US$ 70 a 90.

## 4. Fases

Cada fase pode parar ali sem deixar nada quebrado.

### Fase 0: registro do domínio (feito pela CLI)
Registrado com o contato principal da conta AWS. O Route 53 **cria a zona hospedada sozinho** e aponta
os nameservers do domínio para ela. **Pendente de você:** o e-mail de verificação do contato chega em
`ronybrand@gmail.com` e precisa ser clicado em até 15 dias, senão o domínio é suspenso.

### Fase 1: DNS no Terraform (sem tocar na produção)
- A zona criada no registro é lida como **data source** (`data "aws_route53_zone"`), e não importada nem
  recriada: uma segunda zona teria outros nameservers e não receberia o tráfego, e assim o Terraform
  nunca consegue apagar a zona do registro.
- Novo `terraform/dns.tf`: registro `A` de `api.<domínio>` (TTL 300) apontando para o Elastic IP do
  módulo `portfolio` (`module.portfolio.public_ip`).
- Variável `domain_name` em `terraform/variables.tf`.
- Validação: `terraform plan` mostra 1 recurso a criar, 0 a alterar e 0 a destruir; depois do `apply`
  (feito só após o merge, para não gerar drift contra o `master`) e da conclusão do registro,
  `dig +short api.<domínio>` deve devolver `54.94.231.248`.

### Fase 2: Caddy atendendo os dois nomes
- `deploy/proxy/Caddyfile`: o bloco hoje começa em `54.94.231.248.sslip.io {`. Passar a
  `54.94.231.248.sslip.io, api.<domínio> {`, mantendo o `sslip.io` durante a transição.
- Instalar no servidor (`~/proxy/Caddyfile`, com cópia de segurança) e recarregar o Caddy. Ele emite o
  certificado do Let's Encrypt para o novo nome sozinho.
- Validação antes de seguir: `curl -I https://api.<domínio>/estado/paginado?size=1` com HTTP 200 e
  certificado válido. O CloudFront só pode apontar para o novo nome depois disso, porque ele se conecta
  ao origin por HTTPS usando o nome do origin como SNI.

### Fase 3: trocar quem usa o hostname antigo
| Onde | O que muda |
|---|---|
| `terraform/main.tf`, linha 26 | `api_origin_domain = "${module.portfolio.public_ip}.sslip.io"` passa a `api.<domínio>`. O CloudFront atualiza a origem no lugar, sem downtime, em alguns minutos. |
| `terraform/modules/frontend-static/main.tf` | A CSP lista `connect-src ... https://ai-agent.54.94.231.248.sslip.io`, um subdomínio que o Caddy hoje **não serve** (o agent só é alcançável pela rede interna). Aproveitar para remover a entrada obsoleta. |
| `deploy/estado/lib-swap.sh` | `environment_url` do GitHub Deployments está fixo no `sslip.io`. |
| `deploy/estado/.env.example` | `API_ORIGIN_PERMITIDA` de exemplo cita o `sslip.io`, mas o valor real no servidor já é a origem do **frontend** (`https://d3bqbg07tehy1h.cloudfront.net` e a da Vercel). O CORS **não depende** do hostname do API e **não muda**; só o exemplo precisa ser corrigido. |
| `README.md`, `README.pt-BR.md`, `deploy/README.md`, ADR 0013 | Atualizar as URLs. |
| ADR 0003 | Marcar como superado por um **ADR novo (0024)**. |

Validação: o site do CloudFront continua carregando e as chamadas `/api/*` seguem funcionando; o
`/ask` responde 200.

### Fase 4: aposentar o `sslip.io`
Depois de alguns dias estável, tirar o `54.94.231.248.sslip.io` do bloco do Caddy.

**Antes disso (você):** o React na Vercel chama o backend pela variável de ambiente `BACKEND_API_URL`
(fora do repositório), que provavelmente ainda aponta para o `sslip.io`. Trocar o valor para
`https://api.ronybrand.click` nas variáveis do projeto na Vercel e fazer um novo deploy. Sem isso, o
React perde o backend no dia em que o `sslip.io` sair do Caddy.

### Fase 5 (opcional): recriar a instância com disco de 16 GB
Só depois das fases 1 a 4, quando o hostname já não depende do IP.
1. Subir a instância nova (módulo `portfolio-instance`, `root_volume_size = 16`, tipo e região
   escolhidos na decisão 3), com Docker, Caddy, Alloy, a swap de 2 GB e os timers.
2. Restaurar o dump do Postgres do S3 (o banco tem ~1,4 KB).
3. Copiar `.env` dos dois apps (por SSM, sem passar o conteúdo por chat) e os scripts de `deploy/`.
4. Validar pelo IP novo com `curl --resolve api.<domínio>:443:<ip-novo>`.
5. Trocar o registro `A` para o IP novo (TTL baixo antes) e desligar a instância antiga.

Se a região mudar: backups em S3, SSM e IAM continuam; a zona do Route 53 e o CloudFront são globais.
O volume antigo e o snapshot ficam retidos alguns dias como volta.

## 5. Rollback por fase

| Fase | Como desfazer |
|---|---|
| 1 | Remover os registros e a zona do Terraform (a zona custa US$ 0,50/mês enquanto existir). |
| 2 | Restaurar o Caddyfile anterior e recarregar. |
| 3 | Reverter o `api_origin_domain` no Terraform (volta o `sslip.io`, que continua atendido até a fase 4). |
| 4 | Recolocar o host no Caddyfile. |
| 5 | Trocar o `A` de volta para o IP antigo e religar a instância antiga. |

## 6. Riscos

- **Verificação do contato do domínio** (e-mail): sem clicar, o domínio é suspenso.
- **Zona duplicada**: criar uma zona nova em vez de ler a do registro deixa o DNS sem resposta.
- **TLS na origem do CloudFront**: se o Caddy não tiver o certificado novo antes da fase 3, o CloudFront
  devolve 502.
- **Latência**, só se a região mudar: Brasil para a Virgínia fica uns 100 a 140 ms mais lento nas
  chamadas de API não cacheadas.
- **Residência de dados**, só se a região mudar: o Postgres passa a ficar nos EUA (hoje só há dados de
  demonstração).

## 7. Esforço estimado

Fases 1 a 4: uma tarde. Fase 5: mais meio dia, com uma janela curta fora do ar no corte.

## 8. Checklist

- [ ] Nome definido (decisão 1) e escopo definido (decisão 3)
- [x] Domínio registrado (operação enviada), ID da zona anotado
- [ ] Registro concluído e e-mail de verificação do contato clicado
- [ ] Fase 1: registro `A` criado (`terraform apply` após o merge), `dig` confere
- [x] Fase 2: Caddy com os dois hostnames, `curl` com certificado válido
- [ ] Fase 3: CloudFront, CSP, `lib-swap.sh`, READMEs e ADR 0024 (PR aberta; `apply` após o merge)
- [ ] Antes da fase 4: `BACKEND_API_URL` na Vercel apontando para o nome novo
- [ ] Extra: domínio raiz e `www` servindo o Angular (certificado ACM, aliases, CORS do backend e `og:url` do Angular)
- [ ] Fase 4: `sslip.io` removido do Caddy
- [ ] Fase 5, se escolhida
