# ADR 0019: Migração de Maven para Gradle

## Status
Aceito

## Contexto
O build em Maven já tinha acumulado alguns contornos específicos deste projeto:
`annotationProcessorPaths` explícito no `maven-compiler-plugin` (o javac do JDK 25
não descobre annotation processors via classpath puro), concatenação manual de
`argLine` no Surefire para combinar o agente do JaCoCo com o `-javaagent` do
byte-buddy-agent (necessário pro inline mock maker do Mockito em Java 21+), e um
plugin (`maven-dependency-plugin`) só pra copiar o jar do byte-buddy-agent pra um
caminho fixo em `target/`. Cada um desses ajustes é pequeno isoladamente, mas
juntos tornam o `pom.xml` mais difícil de ler do que o tamanho do projeto (~28
classes de produção) justificaria.

## Decisão
Migrar de Maven para Gradle (Kotlin DSL), num único PR (clean-cut, sem período
de transição com os dois builds coexistindo):
- Import nativo do BOM do Spring Boot (`platform(SpringBootPlugin.BOM_COORDINATES)`)
  em vez do plugin `io.spring.dependency-management` — este projeto nunca precisou
  de override de versão por propriedade, então o import nativo é suficiente. Precisou
  ser repetido nas configurations `annotationProcessor`, `testAnnotationProcessor` e
  na configuration customizada `byteBuddyAgent`, já que nenhuma delas estende de
  `implementation` por padrão.
- Source set dedicado `integrationTest` substituindo o matching por sufixo `*IT`
  do Failsafe — `gradlew test` roda só unit (rápido), `gradlew integrationTest`
  sobe Postgres real via Testcontainers, `gradlew check` roda os dois mais o
  relatório do JaCoCo.
- O agente do JaCoCo e o `-javaagent` do byte-buddy-agent coexistem sem hack: o
  plugin `jacoco` do Gradle injeta seu próprio agente via `JacocoTaskExtension`
  independente do `jvmArgs` da task, ao contrário do Maven, que exigia concatenar
  manualmente `@{jacocoArgLine}` com o argLine explícito.
- Wrapper do Gradle (`gradlew`/`gradlew.bat`) na versão 9.7.1, a primeira família
  estável capaz de rodar sobre JDK 25 (Gradle 8.x não inicia nessa JVM).

## Alternativas consideradas
- **Manter Maven**: descartado — motivo real documentado acima (contornos
  acumulados de argLine/annotationProcessorPaths/plugin de copy), não só
  preferência por ferramenta.
- **Groovy DSL** em vez de Kotlin DSL: decisão leve, sem diferença funcional
  relevante pro tamanho deste projeto. Kotlin DSL escolhido pelo type-safety e
  suporte de IDE.
- **Transição gradual** com Maven e Gradle coexistindo por um tempo: descartado —
  projeto pequeno o bastante (single-module, ~28 classes de produção) pra não
  precisar, e manter dois builds em paralelo custaria mais do que economizaria.

## Consequências
- Positivo: configuração de build type-safe (Kotlin DSL), sem a concatenação
  manual de `argLine` que o Maven exigia, `Dockerfile` não depende mais de uma
  imagem `maven:*` fixa (o wrapper baixa sua própria distribuição Gradle dentro
  do container).
- Positivo: cobertura de linha do JaCoCo permaneceu praticamente idêntica à
  baseline do Maven (219/221 → 217/219 linhas cobertas, mesmos 92 testes
  unitários, mesmas 27 classes analisadas) — confirma que a tradução das
  dependências e da separação unit/integration test não introduziu regressão.
- Negativo aceito: o binário `gradle-wrapper.jar` fica versionado no repo (prática
  padrão do ecossistema Gradle, mas é um binário em um repo que antes não tinha
  nenhum). Também é um ponto de aprendizado extra pra quem só conhece Maven —
  aceitável dado que este é um projeto pessoal de portfólio, não um repo com
  múltiplos colaboradores externos.
