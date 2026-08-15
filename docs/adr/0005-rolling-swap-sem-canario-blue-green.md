# ADR 0005: Rolling swap com health check, sem canário nem blue-green

## Status
Aceito

## Contexto
O deploy original (`docker compose up -d`) recriava o container da aplicação
no lugar — parava o antigo, subia o novo — deixando uma janela de ~15-20s
(tempo do Spring Boot inicializar) em que o Caddy respondia 502 Bad Gateway.
Isso ficou visível durante a própria sessão de correções, com vários deploys
seguidos.

## Decisão
Um script (`deploy.sh`) substitui o `docker compose up -d` direto: sobe o
container novo com nome temporário, espera até 60s por um `/actuator/health`
saudável, e só então remove o antigo e renomeia o novo pro nome definitivo. Se
o health check falhar, o container novo é descartado e o antigo continua no
ar — a implementação também funciona como proteção contra deploy de uma
imagem quebrada, não só contra a janela de downtime.

## Alternativas consideradas
- **Canário** (rotear uma fatia do tráfego pra versão nova, aumentar aos
  poucos monitorando erros): resolve risco em escala — validar com tráfego
  real antes de virar tudo. Descartado porque a aplicação roda numa única
  instância, sem réplicas nem volume de tráfego que justifique rollout
  gradual.
- **Blue-green** (dois ambientes completos, rollback instantâneo trocando
  qual está "ativo"): resolve o mesmo tipo de risco em escala, com reversão
  mais rápida que um redeploy. Descartado pelo mesmo motivo — exigiria manter
  infraestrutura duplicada rodando o tempo todo, incoerente com o resto do
  setup (uma instância única, sem redundância nenhuma no nível de
  infraestrutura — ver [0002](0002-ec2-compose-vs-ec2-rds.md)).
- **Aceitar o downtime de deploy como está**: era a opção de menor esforço.
  Descartada porque o custo de implementar a versão simples era baixo dado que
  a infraestrutura (Docker, Caddy, Actuator) já existia, e o ganho (zero
  downtime observável, validado com monitoramento contínuo durante um deploy
  real) era desproporcional ao esforço.

## Consequências
- Positivo: deploys não derrubam mais o site; validado end-to-end disparando
  um rebuild manual e monitorando a URL pública a cada 1s durante a troca —
  zero respostas fora de 200.
- Positivo colateral: imagem quebrada não derruba produção (health check
  reprova antes da troca).
- Negativo/inconsistência reconhecida: essa solução protege contra o único
  modo de falha que a equipe controla e testa antes de acontecer (uma versão
  nova quebrada). Não protege contra falhas de infraestrutura fora do
  controle da aplicação — hardware da instância, disponibilidade da zona,
  volume de dados corrompido (ver [0002](0002-ec2-compose-vs-ec2-rds.md)).
  Prioridade consciente: esses riscos de infraestrutura têm mitigação própria
  mais barata (backup de banco, EC2 Auto Recovery) que ainda não foi
  implementada — registrado aqui em vez de deixado implícito, justamente pra
  não passar a impressão de que a lacuna não foi percebida.
