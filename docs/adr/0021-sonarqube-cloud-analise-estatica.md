# ADR 0021: SonarQube Cloud como análise estática, não PMD/Checkstyle/SpotBugs

## Status
Aceito

## Contexto
Uma análise comparativa com os projetos irmãos do portfólio mostrou que este era o único sem
nenhuma ferramenta de análise estática dedicada (`spring-order-api` tem PMD, `fastapi-order-api`
tem bandit, `nest-order-api` tem eslint-plugin-security) — só o compilador e os testes.

## Decisão
SonarQube Cloud (ex-SonarCloud), não PMD/Checkstyle/SpotBugs. O projeto é público com licença OSI,
então entra no tier "free forever" (mesmas features do plano Pro pago, sem cartão de crédito,
sem expiração) — verificado em sonarsource.com/plans-and-pricing/sonarqube-cloud antes de decidir,
não assumido.

**Modo Automatic Analysis** (o app do GitHub instalado na organização analisa o repo sozinho a
cada push/PR, aparece como o check "SonarCloud Code Analysis"), não CI-based Analysis. Chegou-se
a configurar o plugin `org.sonarqube` no Gradle + um step `./gradlew sonar` no CI, mas isso
duplicava o que o Automatic Analysis já fazia e falhava por falta de `SONAR_TOKEN` (que o modo
automático não usa) - removido de volta assim que o Automatic Analysis confirmou estar funcionando
(PR #75). Sem plugin novo no `build.gradle.kts`, sem step novo no `ci.yml`: o Sonar aqui roda
inteiramente fora do repositório de código.

## Alternativas consideradas
- **PMD, mirando o `spring-order-api`**: manteria consistência 1:1 com a referência mais próxima
  (mesma linguagem), mas os projetos irmãos já demonstram uma ferramenta de SAST diferente cada
  um - somar Sonar aqui reforça esse padrão de portfólio (uma ferramenta nova por projeto) em vez
  de só replicar a mesma escolha pela terceira vez.
- **CI-based Analysis (`./gradlew sonar` + `SONAR_TOKEN`)**: dá mais controle e permite importar
  cobertura do JaCoCo pro Sonar (`sonar.coverage.jacoco.xmlReportPaths`) - o Automatic Analysis
  não suporta importar relatório de cobertura externo, só faz análise estática pura. Descartado
  por hora: mais uma peça de configuração (plugin + secret) pra um ganho (cobertura dentro do
  Sonar, já visível separadamente no Codecov) que não paga o custo extra de manutenção.
- **Checkstyle/SpotBugs**: mesma categoria de ferramenta local ao Gradle que PMD - descartadas
  pelo mesmo raciocínio acima.

## Consequências
- Positivo: fecha o gap de análise estática sem custo (tier gratuito verificado) e sem tocar no
  build - zero plugin novo, zero secret pra gerenciar.
- Negativo aceito: sem overlay de cobertura dentro do Sonar (só análise estática) - cobertura
  continua só no Codecov. Migrar pra CI-based Analysis depois é uma mudança pequena e reversível
  se isso vier a importar.
- Negativo aceito: dependência de um serviço externo (sonarcloud.io) pro Quality Gate aparecer no
  PR - mesma categoria de dependência que o Codecov já introduziu neste mesmo repo
  (`codecov.yml`), não um padrão novo.
