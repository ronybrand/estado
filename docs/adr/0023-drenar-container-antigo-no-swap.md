# ADR 0023: Drenar o container antigo no swap em vez de removê-lo na hora

## Status
Aceito

## Contexto
O rolling swap (ADR 0005) sobe o container novo, espera o health check e então `promote`: removia
o antigo e renomeava o novo. Num teste de `/ask` em produção, duas perguntas deram 502 logo depois
do swap do agent, sem relação com o Gemini:

- `HTTP/1.1 header parser received no bytes` (EOF): o backend reutilizou uma conexão keep-alive
  com o container que acabava de ser removido.
- `HTTP connect timed out` em 3 s: o backend ainda usava o IP do container antigo, 23 s depois do
  swap. O cache de DNS da JVM tem TTL de 30 s por padrão.

Reproduzido com uma sonda a cada 7 s (uma pergunta que a guarda de entrada do agent recusa, então
200 sem chamar o Gemini): 1 falha em 17 durante um swap do agent.

## Decisão
`promote` renomeia o container antigo para `<nome>-antigo` (com `restart=no`, para ele não voltar
sozinho se o host reiniciar) e `drenar_antigo`, chamada por último em `deploy.sh` e `rollback.sh`,
espera `DRAIN_SECONDS` (60 s, acima do TTL de DNS e do keep-alive de 30 s) antes de parar
(`docker stop -t 30`) e remover. Vale para os dois repositórios (`estado` e `estado-ai-agent`),
cujos `lib-swap.sh` seguem o mesmo padrão.

A espera é síncrona dentro do script, não em segundo plano: o systemd mata os processos que ficam
depois do fim de um serviço `oneshot`. Um `-antigo` sobrando de uma execução interrompida é
removido no início do próximo `promote`.

## Alternativas consideradas
- **Reduzir o TTL de DNS da JVM** (`sun.net.inetaddr.ttl`) e repetir a chamada uma vez: não
  resolve a conexão keep-alive sozinha e não está claro se o TTL é respeitado no native image (a
  classe pode ser inicializada no build). Fica como complemento possível, não como correção.
- **Drenar em segundo plano** (`sleep` e `docker rm` desacoplados): morre junto com o `oneshot`.
- **IP estável para o container novo**: o Docker não garante isso na rede do Compose.

## Consequências
- Positivo: o swap deixa de produzir 502 para quem tinha IP ou conexão em cache. Medido na EC2:
  0 falhas em 22 sondas (swap do agent), 0 em 30 (swap do backend pelo Caddy) e 0 em 16 já na
  `t3.micro`.
- Negativo aceito: o deploy leva ~60 s a mais, e os dois containers coexistem por esse tempo, então
  o pico de memória dura mais (o valor do pico não muda). Na `t3.micro` o swap do agent chegou a
  645 MB usados, com 68 MB de swap.
- Durante a drenagem, clientes com IP em cache ainda falam com a versão antiga por até 60 s.
