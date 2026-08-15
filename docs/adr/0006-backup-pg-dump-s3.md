# ADR 0006: Backup diário via pg_dump + S3, não WAL archiving nem RDS

## Status
Aceito

## Contexto
[ADR 0002](0002-ec2-compose-vs-ec2-rds.md) registrou a ausência de backup do banco como o risco de
maior retorno entre os que ficaram em aberto — modo de falha é perda de dado, não downtime, e o
custo de correção é baixo comparado ao resto da infraestrutura já construída.

## Decisão
Um `systemd timer` diário roda `pg_dump` dentro do container Postgres, comprime o resultado e
envia pra um bucket S3 privado e criptografado, com lifecycle de 30 dias. A instância assume uma
IAM role restrita a `s3:PutObject` nesse bucket específico — sem permissão de leitura ou exclusão.

## Alternativas consideradas
- **Migrar pra RDS com backups automáticos**: resolveria isso e outras coisas (patching, Multi-AZ
  disponível) de uma vez. Descartado pelo mesmo motivo do ADR 0002 — dobra o custo mensal contra o
  crédito da conta, desproporcional pro estágio do projeto.
- **WAL archiving contínuo** (point-in-time recovery): permite restaurar pra qualquer segundo, não
  só pro último dump diário. Descartado por complexidade — exige um `archive_command` configurado,
  armazenamento contínuo de WAL segments, e um processo de restore bem mais elaborado. Pra uma
  aplicação com poucas escritas por dia, perder no máximo 24h de dados num cenário de desastre é um
  risco aceitável; PITR seria a escolha certa se o volume de escrita justificasse.
- **Permissão de leitura/exclusão pra role da instância**: mais conveniente (a própria instância
  poderia verificar ou limpar backups antigos). Descartado deliberadamente — só `PutObject` significa
  que, mesmo com a instância inteira comprometida, um invasor não consegue ler backups existentes
  nem apagá-los pra cobrir rastro.
- **Tiering pra S3 Glacier**: mais barato por GB armazenado. Descartado por três critérios, todos
  apontando na mesma direção aqui:
  - **Tamanho do objeto vs. mínimo cobrável**: Glacier IR/Flexible cobra por objeto como se tivesse
    no mínimo 128KB, não importa o tamanho real. Com Glacier IR em ~US$0,004/GB contra ~US$0,04/GB
    do Standard em `sa-east-1`, o ponto de equilíbrio fica perto de 22KB por objeto — abaixo disso,
    Glacier sai mais caro que Standard, não mais barato. Os dumps atuais têm menos de 1KB.
  - **Volume agregado**: mesmo ignorando o mínimo por objeto, a diferença de custo total (30
    backups de ~1KB) é uma fração de centavo por mês — não paga a complexidade extra.
  - **Padrão de acesso**: Glacier Flexible/Deep Archive leva de minutos a horas pra devolver o
    dado — aceitável pra arquivo de compliance que nunca se espera tocar, não pra única cópia de
    recuperação, onde velocidade de restore importa mais justamente num incidente real.
  - A conta viraria com banco na casa de dezenas/centenas de MB por dump, retenção longa por
    exigência real, e uma estratégia em camadas (recentes em Standard, antigos em Glacier só pra
    cumprir retenção) — nenhuma dessas condições existe nesta aplicação hoje.

## Consequências
- Positivo: lacuna de maior risco da lista de confiabilidade fechada; custo mensal do bucket é
  irrisório pro tamanho atual do banco.
- Positivo: modelo de permissão write-only limita o dano de uma instância comprometida.
- Positivo: restore testado de ponta a ponta — baixado o backup mais recente, restaurado num
  Postgres descartável (`psql -f` do dump), e o conteúdo conferido bate exatamente com o que estava
  em produção no momento do backup (mesmo id, mesmo timestamp de cadastro). Backup sem teste de
  restore é metade da proteção; esse ciclo foi fechado, não só documentado como pendente.
- Negativo aceito: até 24h de dados podem ser perdidos num desastre entre dois backups diários —
  trade-off consciente contra a complexidade de WAL archiving, dado o volume de escrita desta
  aplicação.
- Em aberto, deliberadamente: os 30 dias de retenção no lifecycle rule são um placeholder, não uma
  decisão pensada — não existe, nesta aplicação, nenhum requisito real (legal, de compliance, ou de
  RPO de negócio) que justifique esse número especificamente em vez de 90 dias, um ano, ou
  indefinido. Numa aplicação real, essa retenção deveria vir de um requisito concreto — por exemplo
  LGPD, se o dado guardado fosse pessoal — e não de uma escolha arbitrária feita só porque o custo
  de manter mais tempo é irrelevante nessa escala. Preferível nomear a lacuna a preencher o número
  e apresentar como se fosse decisão informada.
