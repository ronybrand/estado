# ADR 0021: SonarQube Cloud como análise estática, não PMD/Checkstyle/SpotBugs

## Status
Aceito

## Contexto
Uma análise comparativa com os projetos irmãos do portfólio mostrou que este era o único sem
nenhuma ferramenta de análise estática dedicada (`spring-order-api` tem PMD, `fastapi-order-api`
tem bandit, `nest-order-api` tem eslint-plugin-security) — só o ESLint/mypy/ruff/PMD de cada um,
sem nada equivalente aqui além do compilador e dos testes.

## Decisão
SonarQube Cloud (ex-SonarCloud), não PMD/Checkstyle/SpotBugs. O projeto é público com licença OSI,
então entra no tier "free forever" (mesmas features do plano Pro pago, sem cartão de crédito,
sem expiração) — verificado em sonarsource.com/plans-and-pricing/sonarqube-cloud antes de decidir,
não assumido.

Plugin `org.sonarqube` no `build.gradle.kts`, reaproveitando o relatório do JaCoCo que já existia
(`sonar.coverage.jacoco.xmlReportPaths`) em vez de duplicar a medição de cobertura. CI ganha um
step `./gradlew sonar` (com `SONAR_TOKEN`/`GITHUB_TOKEN`) depois do build, e o checkout passa a
`fetch-depth: 0` - o Sonar usa o histórico do git pra blame/análise de "new code", que um clone
raso deixaria incompleto.

## Alternativas consideradas
- **PMD, mirando o `spring-order-api`**: manteria consistência 1:1 com a referência mais próxima
  (mesma linguagem), mas os projetos irmãos já demonstram uma ferramenta de SAST diferente cada
  um - somar Sonar aqui reforça esse padrão de portfólio (uma ferramenta nova por projeto) em vez
  de só replicar a mesma escolha pela terceira vez.
- **Checkstyle/SpotBugs**: mesma categoria de ferramenta local ao Gradle que PMD - descartadas
  pelo mesmo raciocínio acima.
- **Não fazer nada**: manter o gap. Descartado - análise estática é um sinal esperado num
  portfólio backend, e o custo de fechar (Sonar, gratuito) é baixo.

## Consequências
- Positivo: fecha o gap de análise estática sem custo (tier gratuito verificado, não uma suposição
  de "deve ter free tier").
- Positivo: reaproveita o JaCoCo já configurado - não duplica medição de cobertura, só reporta a
  mesma pro Sonar.
- Negativo aceito: dependência de um serviço externo (sonarcloud.io) pro Quality Gate rodar -
  mesma categoria de dependência que o Codecov já introduziu neste mesmo repo (`codecov.yml`), não
  um padrão novo.
- Negativo aceito: `sonar.organization`/`sonar.projectKey` no `build.gradle.kts` foram
  preenchidos com o valor mais provável antes da organização existir de fato em
  sonarcloud.io/create-organization - pode exigir ajuste pontual se o SonarQube Cloud gerar uma
  chave diferente no cadastro.
