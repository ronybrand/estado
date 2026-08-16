# Case study: migrando pra Java 25 / Spring Boot 4.1 e colocando no ar na AWS

**Live**: https://54.94.231.248.sslip.io/ · **ADRs**: [`docs/adr/`](docs/adr/)

Uma API CRUD de unidades federativas do Brasil, parada desde 2018 em Java 8 e Spring Boot
2.0.5.RELEASE, atualizada pra Java 25 e Spring Boot 4.1, e colocada no ar na AWS a partir de uma
conta nova — MFA e IAM primeiro, depois EC2, Docker, Caddy e um pipeline de CI/CD montado do zero.
Este documento conta a história pela sequência real de códigos HTTP que apareceram em produção, na
ordem em que foram encontrados e corrigidos.

**Sequência de status codes em produção**: `404` → `405` → `403` → `502` → `200`

## A migração

O caminho recomendado pela própria Spring pra um salto desse tamanho é incremental — 2.0 → 2.7 →
3.0 → 3.x → 4.0 → 4.1, validando a cada etapa. Sem módulos como Security, Batch ou Cloud em jogo, a
superfície menor tornou o salto direto a escolha melhor — ver [ADR 0001](docs/adr/0001-migracao-direta-java25-springboot4.md).

| | Antes (2018) | Depois (2026) |
|---|---|---|
| Runtime | Java 8 | Java 25 LTS |
| Framework | Spring Boot 2.0.5 | Spring Boot 4.1.0 |
| Namespace | `javax.*` | `jakarta.*` |
| Testes | JUnit 4 | JUnit 5 |
| Credenciais do banco | fixas, commitadas | variáveis de ambiente, rotacionadas |
| CORS | reflete qualquer origem | lista explícita permitida |
| Hospedagem | Heroku | AWS EC2, autogerenciado |

## Construindo do zero

Nenhum histórico prévio na conta AWS — cada camada abaixo foi montada em sequência, do MFA da root
ao primeiro deploy funcionando.

1. **Conta protegida antes de qualquer coisa**: MFA na root, usuário IAM dedicado pro dia a dia, e
   AWS Budgets com alertas em 50/80/100% de um teto de US$30 — antes de existir qualquer instância.
2. **Uma instância EC2, deliberadamente sem RDS**: `t3.small` em `sa-east-1`, security group com
   80/443 abertos e 22 restrito a um único IP — o Postgres nunca ganha porta pública. Rodar o banco
   na mesma máquina em vez de RDS praticamente reduz pela metade o consumo mensal contra o crédito
   da conta — detalhado no [ADR 0002](docs/adr/0002-ec2-compose-vs-ec2-rds.md).
3. **Um pipeline que publica, um timer que puxa**: GitHub Actions builda uma imagem Docker
   multi-stage e publica no GHCR. Um job de deploy via SSH no GitHub foi tentado primeiro e
   descartado — o IP do runner não bate com o security group, e abrir a porta 22 pra internet só
   pra isso não valia a pena. Um timer `systemd` na instância puxa a imagem a cada 5 minutos
   ([ADR 0004](docs/adr/0004-deploy-pull-via-systemd-timer.md)).
4. **HTTPS sem comprar domínio**: Caddy na frente da aplicação, obtendo certificado via Let's
   Encrypt automaticamente contra `<elastic-ip>.sslip.io` — um serviço de DNS wildcard gratuito
   ([ADR 0003](docs/adr/0003-sslip-io-vs-dominio-proprio.md)).

## Quatro bugs em produção

Testes locais e checagens via curl estavam limpos em cada etapa. Os quatro bugs abaixo só
apareceram carregando a aplicação de verdade num navegador — três deles só existem porque um
navegador manda headers que um terminal não manda.

### 404 — front-end chamando um caminho que o proxy não reescrevia
- **Sintoma**: a lista de estados nunca carregava; console mostrava `GET /api/estado/ 404`.
- **Causa raiz**: o build de produção do Angular prefixa toda chamada com `/api`, esperando que um
  proxy remova esse prefixo — exatamente o que o proxy de desenvolvimento local faz. O Caddy em
  produção repassava o caminho sem alterar.
- **Correção**: `handle_path /api/*` no Caddyfile, removendo o prefixo antes de chegar na aplicação.

### 405 — um default do framework que mudou silenciosamente
- **Sintoma**: criar ou editar um estado falhava; o front-end sempre chama `POST`/`PUT` com barra
  final.
- **Causa raiz**: o Spring Framework 7 parou de tratar `/estado` e `/estado/` como equivalentes por
  padrão — era `true` até o Boot 2.x que essa app rodou por oito anos, exatamente por isso ninguém
  nunca tinha esbarrado nisso.
- **Correção**: a primeira tentativa chamou uma API removida no Framework 7.0 sem aviso claro na
  documentação — pego pelo CI falhando o build da imagem, confirmado via `javap` no jar real.
  Resolvido com um bean `UrlHandlerFilter` que normaliza a barra final pra qualquer rota, com o
  raciocínio contra uma config global documentado no [ADR 0005](docs/adr/0005-rolling-swap-sem-canario-blue-green.md).

### 403 — corrigir o bug anterior expôs uma lacuna de CORS
- **Sintoma**: a mesma requisição que funcionava via terminal era bloqueada vinda do navegador, assim
  que a barra final passou a chegar de fato no controller.
- **Causa raiz**: navegadores anexam header `Origin` em requisições `POST`/`PUT` com JSON mesmo
  sendo mesma origem. A config de CORS do Spring ainda apontava pro default de desenvolvimento
  local, `localhost:8000`.
- **Correção**: origem real setada via variável de ambiente — que também precisou ser referenciada
  explicitamente em `environment:` no `docker-compose.yml`; um valor só no `.env` não é injetado
  automaticamente no container.

### 502 — todo redeploy tinha um buraco de 15-20 segundos
- **Sintoma**: o site caía sozinho brevemente, sem relação com os três bugs acima — toda vez que uma
  imagem nova chegava.
- **Causa raiz**: o deploy substituía o container da app no lugar — parava, depois subia. O Caddy
  ficava sem nada pra rotear enquanto o Spring Boot inicializava.
- **Correção**: uma troca gradual (rolling swap) — sobe o container novo com nome temporário, espera
  até 60s por `/actuator/health` saudável, só então remove o antigo e renomeia o novo pro lugar. Um
  health check reprovado agora mantém a versão anterior no ar em vez de derrubar o site —
  raciocínio completo, incluindo por que não canário nem blue-green, no
  [ADR 0005](docs/adr/0005-rolling-swap-sem-canario-blue-green.md). Validado forçando um rebuild e
  monitorando a URL a cada 1s durante a troca: zero respostas fora de 200.

## Decisões registradas

Escritas como ADRs junto com o código, não depois do fato — cada uma registra o que foi escolhido,
o que foi descartado, e por quê.

| ADR | Escolhido | Descartado |
|---|---|---|
| [0001](docs/adr/0001-migracao-direta-java25-springboot4.md) | Salto direto pra Java 25 / Boot 4.1 | Caminho incremental 2.0→2.7→3.0→3.x→4.0→4.1 |
| [0002](docs/adr/0002-ec2-compose-vs-ec2-rds.md) | Postgres em container, mesma instância | RDS gerenciado |
| [0003](docs/adr/0003-sslip-io-vs-dominio-proprio.md) | sslip.io + Caddy pra HTTPS | Domínio próprio via Route 53 |
| [0004](docs/adr/0004-deploy-pull-via-systemd-timer.md) | Timer systemd puxando do GHCR | GitHub Actions empurrando via SSH |
| [0005](docs/adr/0005-rolling-swap-sem-canario-blue-green.md) | Rolling swap com health check | Canário, blue-green |
| [0006](docs/adr/0006-backup-pg-dump-s3.md) | pg_dump diário para S3, write-only | WAL archiving contínuo, RDS |
| [0007](docs/adr/0007-ec2-auto-recovery.md) | Alarme CloudWatch + EC2 Auto Recovery | Multi-AZ com load balancer |
| [0008](docs/adr/0008-rollback-manual-por-tag-registrada.md) | Rollback manual via tag registrada | Histórico de N tags, pipeline de rollback automático |
| [0009](docs/adr/0009-prune-semanal-de-imagens-dangling.md) | Prune semanal de imagens dangling | `docker system prune -a`, aumentar o volume |
| [0010](docs/adr/0010-encriptar-volume-raiz-ebs.md) | Criptografar volume EBS raiz | Registrar como risco aceito |
| [0011](docs/adr/0011-terraform-import-sem-terragrunt.md) | Terraform via import, state local | Recriar do zero, backend S3 desde o início, Terragrunt |
| [0012](docs/adr/0012-grafana-cloud-alloy-observabilidade.md) | Grafana Cloud + Alloy, push direto | Datadog, CloudWatch Agent + SNS, Grafana lendo do CloudWatch, Prometheus self-hosted |

## Postura de confiabilidade: o que está coberto, o que é risco em aberto

O rolling swap fecha o único modo de falha totalmente sob controle deste projeto. O backup fecha o
de maior risco entre os que não estavam. O que ainda falta é nomeado aqui em vez de deixado pra um
revisor achar primeiro.

- ✅ **Deploys ruins**: com health check antes de qualquer tráfego chegar na versão nova; falha no
  check mantém a anterior rodando.
- ✅ **Downtime de deploy**: rolling swap, validado com polling contínuo durante um redeploy real —
  zero respostas fora de 200.
- ✅ **Perda de dado por corrupção/exclusão acidental**: `pg_dump` diário pra um bucket S3 write-only
  (a instância não consegue ler nem apagar backups já enviados, mesmo comprometida). Restore
  testado de ponta a ponta — backup restaurado num Postgres descartável, conteúdo conferido igual ao
  que estava em produção — ver [ADR 0006](docs/adr/0006-backup-pg-dump-s3.md).
- ✅ **Falha de hardware/hypervisor da instância**: alarme do CloudWatch na métrica
  `StatusCheckFailed_System` aciona o `EC2 Auto Recovery` nativo da AWS — recuperação preserva
  Elastic IP, volumes EBS e instance ID. Monitoramento básico (gratuito), detecção em ~10min —
  validado com o alarme em estado `OK`, recebendo dados reais da instância. Ver
  [ADR 0007](docs/adr/0007-ec2-auto-recovery.md).
- ✅ **Esgotamento de recurso dentro do SO (disco/memória)**: achado numa revisão red-team pós-
  Terraform — nenhum alarme cobria isso, só falha de hardware (ver item anterior). Fechado com Grafana
  Alloy publicando métricas de host pro Grafana Cloud, alertas configurados na própria UI do Grafana.
  Ver [ADR 0012](docs/adr/0012-grafana-cloud-alloy-observabilidade.md).
- ✅ **Crash espontâneo do container da app**: achado revisando o próprio ADR 0007 — o `deploy.sh`
  subia a app sem `--restart`, diferente do Postgres/Caddy. Corrigido com `unless-stopped` e
  validado matando o processo de dentro do container (não `docker kill`, que o Docker trata como
  parada intencional): voltou sozinho em menos de 30s.
- ✅ **Versão nova saudável mas funcionalmente quebrada**: o health check do rolling swap não pega
  regressão de lógica, só "processo de pé". `deploy.sh` grava a tag da versão substituída a cada
  troca; `rollback.sh` reaplica essa tag (ou uma explícita) com o mesmo swap com health check. Ver
  [ADR 0008](docs/adr/0008-rollback-manual-por-tag-registrada.md).
- ✅ **Dado em repouso sem criptografia**: achado numa auditoria de inventário pro Terraform, não
  numa ADR anterior — o volume EBS raiz nunca teve o flag ligado. Corrigido via snapshot → cópia
  criptografada → troca do volume raiz, validado com a aplicação respondendo `200` depois do corte.
  Ver [ADR 0010](docs/adr/0010-encriptar-volume-raiz-ebs.md).
- ✅ **Drift silencioso depois de reconciliar via SSH**: revisão de código sobre o `deploy/`
  reconciliado (commit `7d42c48`) achou que a sincronização contra o servidor real tinha revertido
  duas proteções — a captura de log do container no path de falha do swap, e o sufixo `UTC` explícito
  do timer de backup (rodando 3h atrasado sem ninguém notar) — e apontou duas lacunas no Terraform:
  `metadata_options` nunca declarado (IMDSv2 não garantido pelo código, mesmo já ativo na instância
  real) e `admin_cidr` sem `validation` block contra `0.0.0.0/0`. Todas as quatro corrigidas e
  validadas: diff limpo contra o servidor, `terraform plan` sem mudanças, próximo disparo do timer
  conferido em `06:02 UTC`, app respondendo `200` depois do deploy do script atualizado.
- ⚠️ **Falha de zona de disponibilidade inteira**: `t3.small` único, zona de disponibilidade única,
  sem load balancer. Um setup multi-AZ custaria mais por mês do que a instância inteira custa hoje
  — não se justifica nessa escala, deixado de fora por escolha, não por descuido.

## De clicado pra revisável: terraformando a infra existente

As ADRs acima documentam o raciocínio de cada recurso AWS desde o início, mas os recursos em si
sempre foram criados manualmente (console ou `aws` CLI direto) — nunca existiram como código. Os 11
recursos reais (instância, Security Group, Elastic IP, IAM role/instance profile, bucket S3, alarme
CloudWatch) foram trazidos pro Terraform via `terraform import`, comparando cada atributo do código
contra o estado real da conta até `terraform plan` mostrar zero criações ou destruições — só então
qualquer `apply` foi considerado seguro. Ver [ADR 0011](docs/adr/0011-terraform-import-sem-terragrunt.md).

O processo pegou um bug real antes de qualquer mudança em produção: a `description` do Security
Group, escrita de memória durante o código, não batia com o valor já existente — e como esse campo é
imutável na AWS, um `apply` teria forçado destroy+recreate do SG de produção. Corrigido comparando
contra a realidade antes de aplicar, não depois de quebrar algo.

Estrutura em módulos (`portfolio-instance` compartilhado, `app-backup` parametrizado por app) pensada
pro próximo app do portfólio entrar como bloco novo no mesmo `main.tf`, sem duplicar código nem
criar um state novo — o que também é o motivo de Terragrunt ter sido avaliado e descartado por
enquanto (ver ADR 0011): o valor dele aparece com múltiplos state/root modules, e este projeto tem
um só.

## De caixa-preta a observável: métricas, logs e correlação de request

Até aqui, saber se a aplicação estava saudável dependia inteiramente do health check do rolling swap
("processo respondeu") — nenhuma visibilidade de dentro. Fechado com Grafana Cloud + Alloy
([ADR 0012](docs/adr/0012-grafana-cloud-alloy-observabilidade.md)): métrica de host, métrica da
aplicação (JVM, latência/erro por endpoint com histograma real — não só média — via
`/actuator/prometheus`), log de container e log das units systemd, tudo empurrado direto pra nuvem
sem custo incremental na AWS.

O trabalho revelou dois problemas reais no código que existiam antes, mas nunca tinham sido testados
de verdade:
- **Vazamento de detalhe interno**: o handler de `DataIntegrityViolationException` devolvia o SQL
  bruto e o nome da constraint do banco direto pro cliente — só apareceu validando o
  `/actuator/prometheus` contra um Postgres real, forçando uma violação de constraint de propósito.
  Corrigido com mensagem genérica ao cliente; detalhe completo continua só no log, pro operador.
- **Correlação de request**: nem cliente nem log tinham como se referenciar mutuamente antes disso —
  um `RequestIdFilter` (backend) e um interceptor equivalente (frontend Angular, reaproveitando o
  mesmo id nas tentativas de retry) fecham esse ciclo. Validado contra produção: um 500 de teste
  seguro (`DELETE` num id inexistente, sem apagar nada real) voltou pro cliente com um `requestId`, e
  a mesma string apareceu na linha exata do log de erro no Grafana.

Acesso à instância também precisou de ajuste no meio do caminho: SSH restrito por IP parou de bastar
quando a rede móvel usada como contingência passou a bloquear a porta 22 de saída. Resolvido
habilitando AWS Systems Manager Session Manager como caminho alternativo, autenticado por IAM em vez
de endereço de origem — sem abrir mão do SSH restrito já existente.

Os dois alertas criados (5xx da aplicação, ausência de backup em 26h) foram validados dificultando de
propósito, não só configurados: um 500 real e seguro disparado contra produção revelou que `sum()` do
PromQL sobre uma métrica sem nenhuma ocorrência retorna vazio, não zero — o alerta estava notificando
sempre que **não** havia erro, o oposto do desejado. Corrigido (`Alert state if no data` de `NoData`
pra `OK`) e revalidado disparando o mesmo 500 de novo antes de considerar fechado.
