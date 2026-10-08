# ADR 0022: Backend em native image (GraalVM) para caber em instância menor

## Status
Aceito (em observação em produção desde 2026-10-08)

## Contexto
A instância `t3.small` (2 GB) roda backend, `estado-ai-agent`, Postgres, Caddy e Alloy. Reduzir
custo pedia uma `t3.micro` (1 GB), mas o rolling swap (ADR 0005) mantém dois backends no ar ao
mesmo tempo e o pico de memória ficava em ~942 MB, acima do que a `t3.micro` oferece (~920 MB).
Afinar a JVM (SerialGC, `-Xmx`, Metaspace, pool do Hikari) tirou ~44 MB do pico, mas o backend
seguia em ~210 MB anônimos, dos quais ~92 MB só de Metaspace das ~20 mil classes carregadas.

## Decisão
Compilar o backend como native image (`Dockerfile.native`), mantendo o `Dockerfile` JVM como
caminho de volta. Medido no GitHub Actions e depois em produção:

| | JVM (afinada) | Native |
|---|---|---|
| Memória anônima do backend | ~210 a 290 MB | 77 a 95 MB |
| Subida | 4 a 21 s | 0,5 a 1,1 s |
| Pico do swap na EC2 | 942 MB | 838 MB |

**Regras que ficaram**
- A imagem native é a `latest` publicada pelo `docker-publish.yml` a cada merge no `master`, e o
  timer da EC2 a puxa sozinho (ADR 0004). A imagem JVM continua sendo publicada em paralelo, como
  `jvm-latest` e `jvm-<sha>`, para ter volta imediata: `rollback.sh jvm-<sha>`. Se o build native
  falhar (por exemplo, metadata de reflexão desatualizado), a `latest` não muda e a EC2 segue na
  imagem atual. Até 2026-10-08 a publicação era manual (`native-<sha>`), durante a observação em
  produção.
- O `reachability-metadata.json` (reflexão, recursos) é gerado pelo agente de rastreamento do
  GraalVM exercitando a API real (`.github/native-smoke.sh`, workflow `native-metadata.yml`) e
  fica versionado em `src/main/resources/META-INF/native-image/`. Regerar quando o código ou as
  dependências mudarem.
- O check `Native Image` (PRs e `master`) builda a imagem final e roda o mesmo smoke contra um
  Postgres de serviço, e valida que as séries do dashboard (ADR 0012) continuam expostas.

**Hibernate 7 e o BytecodeProvider.** Foi o bloqueio mais caro: o `BytecodeProviderInitiator` do
Hibernate 7.4 ignora `hibernate.bytecode.provider` e escolhe o provedor só via ServiceLoader (sem
serviço registrado cai no provedor no-op). O agente grava `META-INF/services/...BytecodeProvider`
como recurso, o que forçava o ByteBuddy no native, que não pode definir classes em runtime.
O workflow de metadata filtra essa entrada (e a reflexão do `BytecodeProviderImpl`). Sem esse
filtro, nenhuma combinação de variável de ambiente, argumento ou `-D` resolve.

## Alternativas consideradas
- **AppCDS / cache AOT do JDK 25**: o CDS dinâmico tirou só ~24 MB anônimos por JVM (as classes
  passam a vir de um arquivo mapeado), e o cache AOT derruba a JVM com SerialGC e heap pequeno.
  Custo (extrair o jar, +117 MB de imagem, arquivo amarrado ao JDK) maior que o ganho.
- **Deploy sequencial** (parar o antigo antes de subir o novo): resolve o pico, mas com ~20 s de
  downtime a cada deploy. Fica como plano B.
- **Tirar o agent da instância**: muda a arquitetura e o isolamento (ele hoje só é alcançável
  pela rede interna).
- **Mudar de região** (`us-east-1`, ~38% mais barata): migração maior (o IP novo muda o domínio
  `sslip.io`, o Caddy, o CloudFront e o CORS).

## Consequências
- Positivo: backend com ~1/3 da memória e subida em ~1 s; o rolling swap deixa de ser o gargalo.
- Negativo aceito: build native leva ~4 a 8 min no CI e o metadata precisa ser regerado quando o
  código ou as dependências mudam. Um hint faltando só aparece em runtime, por isso o smoke
  cobre login, CRUD, validação, 404 e `/ask`.
- Negativo aceito: o native expõe menos métricas `jvm_*` (nenhuma é usada no dashboard).
- Negativo aceito: perda de ferramentas de debug ad-hoc da JVM (`jstack`, heap dump,
  Java Flight Recorder) — o binário native não roda sob a JVM, então essas ferramentas não se
  aplicam. Diagnóstico em produção passa a depender dos logs (Alloy/Grafana) e do smoke
  (`.github/native-smoke.sh`); um caso que hoje pediria `jstack`/heap dump exige reproduzir local
  com a imagem `jvm-<sha>` equivalente (mantida publicada em paralelo, ver regra acima) ou
  `rollback.sh jvm-<sha>` para investigar sob JVM normalmente.
- O `estado-ai-agent` continua em JVM (Spring AI em native não foi avaliado).
- O cache de camadas do Buildx no GitHub Actions compete com os demais caches do repositório
  (teto de 10 GB, apagados após 7 dias sem uso e não ajustáveis sem método de pagamento): o
  primeiro build depois de uma pausa longa volta a ser frio.
