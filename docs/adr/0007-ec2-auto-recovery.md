# ADR 0007: EC2 Auto Recovery via alarme do CloudWatch

## Status
Aceito

## Contexto
[ADR 0002](0002-ec2-compose-vs-ec2-rds.md) registrou como risco aceito conscientemente a ausência
de qualquer failover de infraestrutura: instância única, zona de disponibilidade única, sem
detecção automática de "a instância morreu". Multi-AZ com load balancer resolveria isso, mas custa
mais por mês do que a instância inteira — descartado por desproporcional. Faltava, porém, uma
mitigação parcial e praticamente gratuita pro caso específico de falha de hardware/hypervisor
subjacente à instância.

## Decisão
Um alarme do CloudWatch (`estado-ec2-auto-recovery`) monitora a métrica `StatusCheckFailed_System`
da instância — o "system status check" da AWS, que reflete problemas de hardware/rede/hypervisor
do host físico, não do sistema operacional dentro da instância. Se o check falhar por 2 períodos
consecutivos de 5 minutos, o alarme dispara a ação nativa `arn:aws:automate:<region>:ec2:recover`,
que migra a instância pra um host saudável, preservando volumes EBS, IP privado e a associação do
Elastic IP.

## Alternativas consideradas
- **Multi-AZ com load balancer**: cobriria esse caso e também falha de zona de disponibilidade
  inteira. Descartado pelo mesmo motivo do ADR 0002 — custo mensal desproporcional ao estágio do
  projeto.
- **Monitoramento detalhado (period de 60s em vez de 300s)**: detectaria a falha e recuperaria mais
  rápido (minutos em vez de ~10 min). Descartado porque monitoramento detalhado tem custo mensal
  por instância, e o monitoramento básico (gratuito, granularidade de 5 min) é suficiente pra esse
  cenário — não é uma aplicação com SLA que justifique pagar por detecção mais rápida.
- **`StatusCheckFailed_Instance` (reboot automático em falha de SO)**: cobre uma classe de falha
  diferente (problema dentro do sistema operacional da instância, não do hardware). Não configurado
  nesta rodada — fica registrado como extensão natural se justificar depois, não implementado agora
  pra não expandir escopo além do que foi pedido.

## Consequências
- Positivo: cobre o cenário de "hardware da instância falhou" citado como risco em aberto no ADR
  0002, sem custo adicional relevante (o alarme do CloudWatch em si é gratuito nessa quantidade).
- Positivo: recuperação preserva Elastic IP, volumes EBS (incluindo os dados do Postgres) e o
  instance ID — não é uma instância nova, é a mesma restaurada num host diferente.
- Negativo aceito: não cobre falha de zona de disponibilidade inteira nem falha de aplicação dentro
  do SO (isso já é coberto por outro mecanismo — o rolling swap com health check do
  [ADR 0005](0005-rolling-swap-sem-canario-blue-green.md) — mas só para deploys, não para uma
  falha espontânea do container fora de um ciclo de deploy).
- Detecção em ~10 minutos (2 períodos de 5 min), não instantânea — trade-off aceito contra o custo
  de monitoramento detalhado.
