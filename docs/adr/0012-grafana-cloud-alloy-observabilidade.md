# ADR 0012: Observabilidade via Grafana Cloud + Alloy, push direto sem custo AWS

## Status
Aceito

## Contexto
O único sinal de saúde da instância até aqui é o alarme do [ADR 0007](0007-ec2-auto-recovery.md)
(`StatusCheckFailed_System`), que cobre falha de hardware/hypervisor — não esgotamento de recurso
dentro do próprio SO. Disco cheio ou memória sob pressão no `t3.small` derrubariam a stack (Postgres,
app, Caddy) silenciosamente, sem nenhum alarme disparando, achado numa revisão red-team pós-Terraform
do [ADR 0011](0011-terraform-import-sem-terragrunt.md). Nem essas métricas existem hoje: disco e
memória não são nativos do EC2/CloudWatch sem um agente rodando dentro da instância.

## Decisão
Grafana Cloud (plano free: 14 dias de retenção, 10k séries de métricas, 50GB de logs, 3 usuários, sem
cartão) + [Grafana Alloy](https://grafana.com/docs/alloy/) rodando na instância, coletando:
- Métricas de host (disco, memória, CPU) via `prometheus.exporter.unix`, empurradas via
  `remote_write`.
- Logs dos containers Docker (`estado-app`, `postgres`, `caddy`) via `loki.source.docker`, lendo
  direto do socket do Docker, empurrados via `loki.write`.
- Logs das units systemd que rodam no host, fora de container (`estado-backup.service`,
  `estado-deploy.service`, `estado-prune.service`) via `loki.source.journal`, filtrado só por essas
  três (não o journal inteiro do host — kernel/sshd/cloud-init seriam ruído).

Ambos direto pro Grafana Cloud — sem passar pelo CloudWatch nem por qualquer API da AWS. O usuário de
sistema `alloy` (criado pelo pacote) precisa estar no grupo `docker` pra ler o socket — mesmo
trade-off que o `ec2-user` já assume pros próprios scripts (grupo `docker` é root-equivalente no
host); não é um problema novo introduzido aqui, só mais um processo operando com esse mesmo nível de
acesso. Pro journal, o grupo é `systemd-journal` — bem mais restrito (só leitura de log), risco novo
praticamente nenhum.

**Extensão posterior**: métricas da própria aplicação (JVM, HTTP por endpoint/status, pool de conexão)
via `/actuator/prometheus` (`micrometer-registry-prometheus`), coletadas com o mesmo Alloy. O container
`estado-app` não publica a porta 8080 pro host de propósito (só vive nas redes Docker internas) —
publicar quebraria o rolling swap sem downtime do [ADR 0005](0005-rolling-swap-sem-canario-blue-green.md):
container antigo e novo coexistem por até 60s durante o health check, e dois containers não podem
publicar a mesma porta do host ao mesmo tempo. Resolvido descobrindo o IP interno do container
dinamicamente via `discovery.docker` (mesmo mecanismo já usado pros logs) em vez de abrir porta nova —
sobrevive a cada rolling swap sem reconfiguração.

Alertas de limiar (disco/memória altos, 5xx da app) são configurados manualmente na UI do Grafana
Cloud (Grafana Alerting), não via Terraform — mesmo raciocínio de proporcionalidade do ADR 0011 pra
não trazer mais um provider Terraform só pra 3-4 regras de alerta.

**Especificação do alerta de 5xx** (a implementar na UI do Grafana Cloud assim que a conta existir —
não dá pra automatizar isso daqui, ver ["Instalar o Grafana Alloy"](../../deploy/README.md#instalar-o-grafana-alloy-uma-vez)
no `deploy/README.md`):

```promql
sum(increase(http_server_requests_seconds_count{application="estado", outcome="SERVER_ERROR"}[10m]))
```
- **Limiar**: `> 0` — qualquer 5xx dispara, não um percentual.
- **Janela**: 10 minutos, avaliada a cada 1-2 min.
- **Contact point**: e-mail (o mesmo já usado na assinatura do SNS/CloudTrail — ver ADR 0011).
- **Por que valor absoluto, não `taxa de erro > 5%`** (o exemplo didático mais comum de alerta): com o
  volume de tráfego desse app (uso pessoal/demonstração), 5% de uma janela de poucas dezenas de
  requisições é 1 erro isolado — um alerta percentual dispararia por ruído estatístico, não por
  problema real, e a resposta natural depois de alguns falsos positivos é passar a ignorar o alerta
  (fadiga de alerta), o que é pior que não ter alerta nenhum.
- **Por que só 5xx, não 4xx+5xx junto**: `outcome="CLIENT_ERROR"` (validação, tipo inválido, sigla
  duplicada) é tráfego *esperado* pelo próprio design do `CustomGlobalExceptionHandler` — alertar
  nisso dispara toda vez que alguém testa a API errado. `outcome="SERVER_ERROR"` só acontece no
  handler catch-all de `Exception` — por desenho, deveria ser raríssimo, então qualquer ocorrência já
  é um sinal real, sem precisar de limiar percentual pra ser confiável.
- **Achado testando ao vivo (2026-08-16)**: `sum()` do PromQL sobre uma métrica que nunca teve nenhuma
  ocorrência (nenhum 5xx desde que a app subiu) não retorna `0` — retorna **sem dado nenhum**. Com
  "Alert state if no data" no padrão (`NoData`), isso disparava notificação toda vez que *não* havia
  5xx (o estado bom), via um alerta sintético `DatasourceNoData` diferente do `estado-5xx` de verdade —
  o oposto do que se queria. Corrigido setando esse campo pra **`OK`** (sem dado = tudo bem, silêncio).
  Validado disparando um 500 de teste seguro (`DELETE /estado/<id-inexistente>`, aciona
  `EntityNotFoundException` no `EstadoRepository.excluir` — não apaga nada real): antes da correção,
  virava `DatasourceNoData`; depois, `alertname=estado-5xx` de verdade, com o valor real do
  `increase()`. Essa mesma armadilha **não** se aplica ao alerta de backup abaixo — lá, "sem dado" é
  literalmente a condição de alarme, então `NoData` continua correto.

**Especificação do alerta de backup** (dead man's switch — dispara tanto se o backup falhar
explicitamente quanto se o timer simplesmente parar de rodar, os dois casos que importam):

```logql
sum(count_over_time({job="integrations/systemd-journal", unit="estado-backup.service"} |= "Backup enviado" [26h]))
```
- **Limiar**: `== 0` (ou `< 1`) — a linha de sucesso (`backup.sh`, ver ADR 0006) não apareceu numa
  janela de 26h, folga sobre o `RandomizedDelaySec=600` do timer diário. Cobre falha explícita
  (`backup.sh` sai com erro antes de logar sucesso) e falha silenciosa (timer desabilitado, instância
  não ligou no horário sem o `Persistent=true` conseguir recuperar) com uma query só.
- **Avaliação**: a cada 30-60min é suficiente — não é uma condição que muda rápido.

**Especificação do dashboard** (`estado — visão geral`, Grafana Cloud → Dashboards): consolidado num
painel só o que antes só dava pra ver um sinal de cada vez no Explore. Queries por painel, pra
reconstruir se o dashboard for perdido — nenhuma delas provisionada via código, mesmo raciocínio de
não trazer o provider Terraform do Grafana só pra isso:

| Painel | Query | Fonte |
|---|---|---|
| Latência P95 | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="estado"}[5m])) by (le))` | Prometheus |
| Taxa de erro 5xx | `sum(rate(http_server_requests_seconds_count{application="estado", outcome="SERVER_ERROR"}[5m]))` | Prometheus |
| CPU | `100 - (avg(rate(node_cpu_seconds_total{instance="estado-portfolio", mode="idle"}[5m])) * 100)` | Prometheus |
| Disco | `100 - (node_filesystem_avail_bytes{instance="estado-portfolio"} / node_filesystem_size_bytes{instance="estado-portfolio"} * 100)` | Prometheus |
| Pool Hikari ativo | `hikaricp_connections_active{application="estado"}` | Prometheus |

As anotações de deploy/rollback (abaixo) aparecem automaticamente como marcador vertical em todo
painel de série temporal, sem configuração extra por painel.

**Anotação de deploy/rollback**: `deploy.sh`/`rollback.sh` mandam uma anotação pra API do Grafana
(`POST /api/annotations`) depois de um swap bem-sucedido, marcando exatamente quando uma versão nova
subiu — pra correlacionar visualmente com mudança de latência/erro no dashboard sem cruzar manualmente
o histórico do GHCR/GitHub Actions com o horário no Grafana. Melhor esforço de propósito
(`annotate_deploy()` em `lib-swap.sh`): credencial ausente ou Grafana fora do ar nunca falham o
deploy, a anotação não faz parte do caminho crítico do swap. Credencial (`GRAFANA_CLOUD_ANNOTATIONS_TOKEN`,
Service Account token, escopo diferente do usado pra métrica/log) fica em `deploy/estado/.env`, não em
`deploy/alloy/.env` — mora onde o script que a consome já lê o ambiente, mesmo que conceitualmente seja
"observabilidade".

Config versionada em `deploy/alloy/` (nível compartilhado da instância, ao lado de `deploy/proxy/` —
não é um app específico do portfólio). Credenciais do Grafana Cloud num `.env` gitignorado na
instância, mesmo padrão do `POSTGRES_PASSWORD` (ver `deploy/README.md`).

## Alternativas consideradas
- **Datadog**: operador já tem experiência prévia (custo de aprendizado zero), mas retenção free de
  só 1 dia (vs. 14 do Grafana) e estrutura de produtos separados (Log Management, Synthetics, RUM) é
  a forma mais comum de um "free tier" virar cobrança surpresa quando alguém liga o módulo errado sem
  perceber. Pro objetivo de "ficar de graça o máximo de tempo possível", pesou mais que a familiaridade.
- **CloudWatch Agent + `aws_cloudwatch_metric_alarm` + SNS**: chegou a ser desenhado (e o gap de
  `metadata_options.http_put_response_hop_limit` não declarado no Terraform foi corrigido nesse
  processo, junto com a trilha do CloudTrail — ambos ficam independentemente da escolha de
  observabilidade), mas descartado antes de implementar: dashboard mais pobre pra portfólio, e
  CloudWatch já aparece no projeto (auto-recovery, CloudTrail) — não acrescentaria ferramenta nova ao
  currículo.
- **Grafana lendo do CloudWatch via API** (em vez do Alloy empurrar direto): geraria custo real na
  AWS — `cloudwatch:GetMetricData` é cobrado por chamada acima da cota grátis — só pra reempacotar
  dado que já existiria. Descartado: o objetivo era manter o custo incremental na AWS em zero, não só
  baixo.
- **Prometheus + Grafana self-hosted**: mais um serviço pra manter vivo, atualizar e escalar disco de
  série temporal, numa infra de operador único que evita esse tipo de carga de propósito (mesmo
  raciocínio dos ADRs 0002/0007/0011).

## Consequências
- Positivo: fecha o gap de esgotamento de recurso (disco/memória) que nenhum alarme cobria até aqui —
  nem o de auto-recovery, que só vê falha de hardware.
- Positivo: zero mudança na AWS pra essa peça especificamente — sem IAM novo, sem recurso Terraform
  novo. O agente fala só com a Grafana Cloud, nunca com a API da AWS.
- Positivo: ferramenta de mercado nova no portfólio, sem redundância com o que o projeto já usa
  (CloudWatch).
- Negativo aceito: retenção de 14 dias no free tier não serve pra análise de tendência de longo prazo,
  só operação corrente. Aceitável pro estágio atual — upgrade pago é um passo natural se isso deixar
  de bastar, não um pré-requisito (mesmo raciocínio do ADR 0011).
- Negativo aceito: regras de alerta vivem na UI do Grafana, fora de código — risco de divergência
  entre o que está documentado aqui e o que está configurado de fato, mesmo risco que a Fase 0 (MFA/
  IAM clicados manualmente) já aceita pro setup inicial da conta.
- Negativo aceito: volume de logs sem controle explícito de nível — se a aplicação for deixada em
  `DEBUG` por engano, o consumo da cota de 50GB/mês sobe rápido sem ninguém notar até estourar.
  Nenhuma salvaguarda automática hoje além de checar o uso manualmente na UI do Grafana Cloud.
- Negativo aceito: alerta de 5xx sozinho não distingue causa — um `DataIntegrityViolationException`
  isolado (que já é WARN, 400, não deveria nem chegar aqui) e uma falha real do catch-all disparam o
  mesmo jeito. Pra esse volume/escala, correlacionar manualmente pelo log no momento em que o alerta
  chega é suficiente; não justifica alertas separados por tipo de exceção.
- **Validado em produção (2026-08-16)**: instalação feita via AWS SSM Session Manager (SSH bloqueado
  por bloqueio de porta 22 da operadora móvel do administrador — motivou o [`terraform/ssm.tf`](../../terraform/ssm.tf),
  ver ADR sobre acesso à instância no `deploy/README.md`). `journalctl -u alloy` sem erro; métrica de
  host, métrica da app (IP dinâmico do container resolvido certo), log de container e log de systemd
  (`relabel_rules` do `loki.source.journal`, a única sintaxe não testada antes) todos confirmados
  chegando com dado real no Explore do Grafana Cloud. Nenhum ajuste de config foi necessário.
- **Os dois alertas (5xx e backup) foram criados e o de 5xx validado disparando de verdade** (ver
  achado do `NoData` acima). O de backup não foi testado ao vivo de propósito — exigiria desabilitar o
  `estado-backup.timer` real pra forçar a ausência, risco desproporcional ao ganho de confiança pra
  uma proteção que o projeto trata como a lacuna de maior risco (ADR 0006). Confiança vem por outra
  via: a string exata logada por `backup.sh` (`"Backup enviado: ..."`) confirmada batendo com o filtro
  da query, e a regra salva sem erro de sintaxe no Grafana.

## Fora de escopo: o que uma aplicação real de produção precisaria além disso

Registrado aqui pra não passar a impressão de que essa stack de observabilidade é "completa" fora do
contexto de portfólio — o que seria proporcional numa aplicação real, com tráfego e time de verdade,
e por que não faz sentido implementar aqui:

- **Alertas correlacionados, não isolados** (RED: rate/errors/duration, ou USE: utilization/
  saturation/errors) — combinar sinais (erro sobe *e* latência sobe *e* pool de conexão satura) em vez
  de um limiar por métrica solto. Aqui, um único operador olhando um e-mail de vez em quando não
  justifica essa sofisticação.
- **Escalonamento/on-call de verdade** (PagerDuty, Opsgenie, rotação) — e-mail sozinho já é adequado
  pra um operador único sem SLA com terceiros. Isso é sobre estrutura de time, não sobre a
  aplicação.
- **Alertas por orçamento de erro (SLO/error budget)** em vez de limiar estático — exige antes ter um
  SLO definido e tráfego real o bastante pra ele significar algo; sem uso real, qualquer SLO aqui
  seria número inventado.
- **Runbook por alerta** (o que fazer quando dispara, não só que algo disparou) — vale a partir do
  momento em que mais de uma pessoa responde a incidente; com um operador só, o contexto já está na
  cabeça de quem vai olhar.
- **Tracing distribuído** (OpenTelemetry/Tempo) — já descartado acima; ganha valor real quando existe
  mais de um serviço se chamando. Aqui é uma app, um banco, sem chamada entre serviços.
- **Log estruturado (JSON)** em vez do padrão texto atual (`%d %-5level [%X{requestId}] ...`) — LogQL
  em campo JSON é mais preciso que regex sobre texto solto; vale a pena quando o volume de log
  justifica automatizar parsing/alerta em cima dele, não pra consulta manual ocasional.
- **Observabilidade de autenticação/autorização** — essa API não tem autenticação nenhuma hoje; uma
  aplicação real precisaria de log de auditoria (quem fez o quê) e métricas de tentativa de acesso
  negado, que simplesmente não existem aqui porque o conceito de "usuário" não existe.
- **Baseline de carga real** — o P95/P99 habilitado nesta sessão é tecnicamente real, mas
  estatisticamente pouco significativo sem tráfego de verdade; numa aplicação real, "normal" se
  estabelece com uso de produção ao longo do tempo, não é assumido.
- **Observabilidade da camada de dados** (slow query log do Postgres, `pg_stat_statements`) — hoje só
  existe métrica de pool de conexão (Hikari), não da query em si; relevante quando o app tem consultas
  complexas o bastante pra precisar identificar qual é lenta, não é o caso desse CRUD simples.
- **Guarda-corpo de custo de observabilidade** (sampling, ajuste de nível por ambiente) — irrelevante
  na escala/cardinalidade desse projeto (bem abaixo dos tetos do free tier), mas uma aplicação real
  precisa ativamente evitar que cardinalidade de métrica ou volume de log cresçam sem controle e
  virem custo real.
