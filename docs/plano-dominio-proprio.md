# Plano: domínio próprio (`.click` na AWS) e, opcionalmente, recriar a instância com disco menor

Status em 2026-10-09: domínio **`ronybrand.click` registrado pela CLI** (1 ano, US$ 3, renovação
automática e proteção de WHOIS ligadas; operação `a354caf6-bce7-49c3-a8fd-0935030841f1`) e zona
hospedada `Z1023450XBS9WJ1974JR` criada pelo registro. Fases 0 e 1 em andamento; as demais ainda não
foram executadas. Escopo da fase 5 decidido: `t4g.micro` em `us-east-1`, 16 GB (detalhe na fase 5).

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
| 3 | Escopo | (a) só domínio e troca de hostname; (b) também recriar a instância com disco de 16 GB em `sa-east-1`; (c) idem em `us-east-1` e/ou ARM (`t4g.micro`). **Decidido em 2026-10-09: (c) com `t4g.micro` em `us-east-1`, 16 GB.** |

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

### Fase 5: recriar a instância em `us-east-1`, `t4g.micro` (ARM), disco de 16 GB
Decisão de 2026-10-09: `t4g.micro` em `us-east-1`, volume gp3 criptografado de 16 GB (uso estável medido:
~7 GB). Economia estimada: ~US$ 9,4 por mês (~US$ 113 por ano) contra a `t3.micro` de 30 GB em `sa-east-1`.
A máquina velha continua intacta até o corte ser validado; o rollback do corte é reverter um registro DNS.

**Fase 5.0: pré-requisitos, sem nenhum efeito no servidor atual**
1. **Imagens multi-arch (`amd64` e `arm64`) no CI**, nos dois repositórios. Um build por arquitetura em
   runners nativos (`ubuntu-latest` e `ubuntu-24.04-arm`, este gratuito em repositório público), publicando
   por digest, e um passo que junta os dois num manifesto com as tags `latest`, `<sha>`, `jvm-latest` e
   `jvm-<sha>`. O native sob emulação QEMU seria lento demais, por isso não se usa `platforms:` num build só.
   O check `Native Image` (PRs) também passa a rodar o smoke em `arm64`.
2. **Módulo `portfolio-instance` sem região fixa**: hoje a AZ é `sa-east-1b` e o ARN do alarme de
   auto-recuperação tem `sa-east-1`; o key pair é regional. Passam a ser variáveis (`availability_zone`,
   região do alarme, `key_name` opcional, já que o acesso é por SSM).
3. **Script de bootstrap versionado** (`deploy/bootstrap/`). A instância atual foi montada à mão (o módulo
   não tem `user_data`), então a configuração só existia no servidor. O script, idempotente, reproduz: Docker
   e plugin do Compose (`arm64`), redes `portfolio` e `estado_internal`, swap de 2 GB com `swappiness=10`,
   limites do journald, diretórios `~/estado`, `~/estado-ai-agent` e `~/proxy`, unidades e timers do systemd,
   Grafana Alloy (RPM `aarch64`) e o drop-in com o limite de memória.

**Fase 5.1: provisionar (sem tráfego)**
4. Segundo módulo da instância no Terraform, com `providers = { aws = aws.us_east_1 }`, `t4g.micro`,
   `root_volume_size = 16`, AMI `arm64` do Amazon Linux 2023 fixada explicitamente (como já se faz hoje) e
   um novo Elastic IP. O `terraform plan` deve mostrar só recursos novos.
5. Rodar o bootstrap pela SSM e instalar os scripts de `deploy/`.
6. **Segredos dos `.env`** (hoje só existem na máquina velha, não há Parameter Store): transferência direta
   máquina a máquina por um objeto S3 temporário com URLs pré-assinadas de poucos minutos (a máquina velha
   envia, a nova baixa, o objeto é apagado em seguida). Os valores não passam pelo meu terminal nem por
   nenhum log, e eu só vejo se o arquivo chegou e quais chaves ele tem.
7. Opcional: copiar também o volume de dados do Caddy (certificados e conta ACME), para o corte não ter
   intervalo de TLS.

**Fase 5.2: dados e validação**
8. `pg_dump` lógico na máquina velha e restauração na nova (portável entre arquiteturas; o banco hoje é
   minúsculo). Conferir contagem de linhas.
9. Validar a máquina nova **antes** do corte, sem mexer em DNS: `curl --resolve api.<domínio>:443:<ip-novo>`
   com o `native-smoke.sh` completo, `/ask` pela guarda de entrada, memória e swap sob um swap de deploy.

**Fase 5.3: corte**
10. Trocar o registro `A` de `api.<domínio>` (TTL 300) para o IP novo em `terraform/dns.tf`. Backups e deploys
    automáticos passam a rodar nas duas máquinas durante a sobreposição; desligar o Alloy da velha para não
    duplicar métricas.
11. Observar 24 a 48 horas. **Rollback:** reverter o registro `A` para o IP antigo (propaga em ~5 min).

**Fase 5.4: desativar a máquina velha**
12. Parar, tirar um snapshot final, remover o módulo antigo do Terraform (instância, EIP, security group,
    alarme) e liberar o IPv4 antigo. A zona, o CloudFront, o ACM e o bucket de backup (`sa-east-1`) não mudam.

**Dimensionamento do disco (medido em 2026-10-09 na `t3.micro` atual)**

Uso estável de 7,0 GB, depois de limpar as imagens de teste:

| Área | Tamanho | Reduzível? |
|---|---|---|
| `/usr` (sistema) | 2,0 GB | não |
| `/swapfile` | 2,0 GB | sim, para 1 GB (a swap usada nunca passou de ~160 MB em dias de observação) |
| `/var/lib/docker` | 1,9 GB | um pouco: o `jvm-latest` (420 MB) só está lá por testes; os timers baixam só a `latest` |
| `/var/cache` | 0,4 GB | sim, com `dnf clean all` |
| Logs e `/boot` | ~0,16 GB | não |

Com swap de 1 GB, sem o `jvm-latest` e com o cache limpo, o uso estável cai para ~5 GB; um deploy com
sobreposição soma ~0,7 GB (imagem nova baixada antes do prune).

- **Piso técnico: 8 GB**, o tamanho do snapshot da AMI do Amazon Linux 2023 padrão (a ECS-optimized atual
  tem 30 GB, então a máquina nova usa a padrão). **Piso prático: 10 a 12 GB.**
- Preço em `us-east-1`: 16 GB = US$ 1,28 por mês, 12 GB = 0,96, 10 GB = 0,80, 8 GB = 0,64. Ir de 16 para 12 GB
  economiza **US$ 0,32 por mês (~US$ 4 por ano)**, pouco para o risco de disco cheio (derruba o banco e os
  containers).
- **Decisão: 16 GB** (uso previsto ~36%). O EBS cresce online, sem reboot, mas não encolhe; por isso 12 GB só
  se justificaria com os itens abaixo e um alerta de disco.

Medidas que entram no bootstrap, qualquer que seja o tamanho do disco:
1. `/etc/docker/daemon.json` com `log-driver: json-file`, `max-size: 10m`, `max-file: 3`. Hoje os containers
   usam `json-file` **sem rotação** (nenhum `daemon.json`); os logs são de poucos KB, mas nada os limita.
2. `dnf clean all` ao fim da instalação.
3. Swap de 1 GB (`vm.swappiness=10`, como hoje).
4. Não baixar o `jvm-latest` na máquina; ele existe no GHCR para um rollback pontual.
5. **Alerta de disco em 80%** no Grafana (o dashboard já tem o painel de disco, mas não há alerta). Sem ele,
   não reduzir abaixo de 16 GB.

**Custo durante a sobreposição:** as duas máquinas ligadas por 1 a 2 dias, uns US$ 0,7 por dia a mais.

**Riscos específicos desta fase**
- **Latência:** o CloudFront do Brasil passa a buscar as chamadas de API não cacheadas nos EUA (uns 100 a
  140 ms a mais). O `/ask` (15 a 30 s) nem sente.
- **Residência de dados:** o Postgres passa a ficar nos EUA (hoje só há dados de demonstração).
- **Imagem `arm64` nunca rodou em produção:** daí a validação da 5.2 antes do corte.
- **Certificado do Caddy:** sem copiar o volume de dados, o novo host só obtém o certificado depois de o DNS
  apontar para ele (poucos segundos de falha de TLS no corte).

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
- [ ] Fase 5.0: imagens multi-arch no CI (estado e agent)
- [ ] Fase 5.0: módulo da instância sem região fixa
- [ ] Fase 5.0: script de bootstrap versionado (com rotação de logs do Docker, swap de 1 GB e `dnf clean all`)
- [ ] Fase 5.0: alerta de disco em 80% no Grafana
- [ ] Fase 5.1: instância nova provisionada e configurada, segredos transferidos
- [ ] Fase 5.2: banco restaurado e máquina nova validada com `--resolve`
- [ ] Fase 5.3: corte do registro `A` e observação de 24 a 48 h
- [ ] Fase 5.4: máquina velha desativada e removida do Terraform
