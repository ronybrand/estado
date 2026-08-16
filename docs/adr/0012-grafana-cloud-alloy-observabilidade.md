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

Ambos direto pro Grafana Cloud — sem passar pelo CloudWatch nem por qualquer API da AWS. O usuário de
sistema `alloy` (criado pelo pacote) precisa estar no grupo `docker` pra ler o socket — mesmo
trade-off que o `ec2-user` já assume pros próprios scripts (grupo `docker` é root-equivalente no
host); não é um problema novo introduzido aqui, só mais um processo operando com esse mesmo nível de
acesso.

Alertas de limiar (disco/memória altos) são configurados manualmente na UI do Grafana Cloud (Grafana
Alerting), não via Terraform — mesmo raciocínio de proporcionalidade do ADR 0011 pra não trazer mais
um provider Terraform só pra 1-2 regras de alerta.

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
