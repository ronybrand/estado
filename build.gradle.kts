import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("jacoco")
    id("org.graalvm.buildtools.native") version "0.11.0"
}

group = "br.com.rony.spring.boot"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

repositories {
    mavenCentral()
}

// GAs com override de versao acima do BOM abaixo (so o group:name, sem
// versao) - usado pelo checkCveOverrides para saber quais constraints
// verificar. A versao pinada em si fica so na declaracao literal da
// constraint, nunca duplicada aqui.
val cveOverrideCoordinates = setOf(
    "org.apache.tomcat.embed:tomcat-embed-core",
    "com.fasterxml.jackson.core:jackson-core",
    "com.fasterxml.jackson.core:jackson-databind",
    "tools.jackson.core:jackson-core",
    "tools.jackson.core:jackson-databind",
    // commons-lang3 fica de fora de proposito: o BOM do Spring Boot ja
    // resolve a versao patcheada sozinho (3.20.0), a constraint dela abaixo
    // existe so pro Dependabot ter algo literal pra reconhecer, nao porque
    // o BOM esta atrasado - checkCveOverrides a flagaria como "redundante"
    // incorretamente se entrasse aqui.
)

// Import nativo do BOM do Spring Boot em vez do plugin
// io.spring.dependency-management - este projeto nunca precisou de override
// de versao por propriedade, entao nao ha motivo pra carregar um segundo
// plugin so pra isso.
dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    // CVEs com patch publicado rio acima do que o BOM do Spring Boot 4.1.1
    // (unica versao 4.1.x no Maven Central ate agora) resolve - ver
    // Dependabot alerts #2-18 e checkCveOverrides abaixo, que detecta quando
    // um override vira redundante (BOM alcancou ou passou a versao pinada)
    // pra poder ser removido.
    //
    // Declaradas literalmente de proposito, nao geradas via loop/interpolacao
    // de string a partir de um Map: o parser do Dependabot para Gradle
    // Kotlin DSL so reconhece dependencias escritas assim - uma versao
    // anterior gerada dinamicamente fez os jobs de "security update" do
    // Dependabot falharem com security_update_dependency_not_found (erro
    // vermelho recorrente no Actions da main, nao so alerta ignorado).
    constraints {
        // Tomcat embutido: Incorrect Authorization (FORM auth), Authentication
        // Bypass (DIGEST, capture-replay) e Improper Access Control - todos
        // criticos, corrigidos em 11.0.25 (BOM resolvia 11.0.24).
        implementation("org.apache.tomcat.embed:tomcat-embed-core:11.0.26") {
            because("CVEs criticos corrigidos em 11.0.25+ (BOM do Spring Boot 4.1.1 ainda resolve 11.0.24)")
        }
        // Jackson 2.x: puxado pelo springdoc-openapi/swagger-core, que ainda
        // nao migrou pro Jackson 3 nativo do Spring Boot 4 - ver comentario
        // em AskProxyService/RateLimitFilter sobre os dois ObjectMapper
        // coexistindo.
        implementation("com.fasterxml.jackson.core:jackson-core:2.22.3") {
            because("ReDoS/DoS corrigidos em 2.22.3 (BOM resolve 2.22.1)")
        }
        implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3") {
            because("Multiplos CVEs corrigidos em 2.22.2/2.22.3 (BOM resolve 2.22.1)")
        }
        // Jackson 3.x: o ObjectMapper nativo do Spring Boot 4 (tools.jackson.*).
        implementation("tools.jackson.core:jackson-core:3.1.7") {
            because("ReDoS/DoS corrigidos em 3.1.7 (BOM resolve 3.1.5)")
        }
        implementation("tools.jackson.core:jackson-databind:3.1.7") {
            because("Multiplos CVEs corrigidos em 3.1.6/3.1.7 (BOM resolve 3.1.5)")
        }
        // Uncontrolled Recursion (StackOverflow) processando entrada longa -
        // o Gradle ja resolvia esta mesma versao via conflict resolution com
        // outro modulo mesmo antes desta constraint (nao era um gap real de
        // seguranca), mas sem versao explicita o job de "security update" do
        // Dependabot nao encontra nada literal pra reconhecer como corrigido
        // e falha com security_update_dependency_not_found a cada tentativa.
        implementation("org.apache.commons:commons-lang3:3.20.0") {
            because("Uncontrolled Recursion corrigido em 3.18.0+ (ja resolvido em 3.20.0 via BOM/conflict resolution, versao explicita so pro Dependabot reconhecer)")
        }
    }

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Traz HttpClientSettings/ClientHttpRequestFactoryBuilder (modulo
    // spring-boot-http-client, Boot 4) - usado em AskProxyClientConfig pra
    // configurar timeout no RestClient que chama o estado-ai-agent.
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Schema versionado por migration (db/changelog/) em vez de
    // hibernate.ddl-auto:update - ver ADR 0014.
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Swagger UI + OpenAPI - habilitado por padrao (dev-friendly), desligado
    // explicitamente em producao via env var no lib-swap.sh, mesmo padrao ja
    // usado pro CORS (API_ORIGIN_PERMITIDA) neste application.yml.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    // Rate limiting em memoria por IP (ADR 0016) - biblioteca Java pura, sem
    // dependencia de Redis/store externo, adequada a uma unica instancia.
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    // Cache com expiracao para os buckets do rate limiter - sem isso, o mapa
    // de IPs cresceria indefinidamente (vazamento de memoria), ja que nunca
    // ha eviction de um ConcurrentHashMap puro.
    implementation("com.github.ben-manes.caffeine:caffeine")
    // Autenticacao JWT self-issued, usuario admin unico (ADR 0017).
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    implementation("org.postgresql:postgresql")
    implementation("org.apache.commons:commons-lang3")

    compileOnly("org.projectlombok:lombok")
    // annotationProcessor nao estende de implementation, entao o platform()
    // precisa ser importado aqui de novo pra resolver as versoes gerenciadas
    // pelo BOM do Spring Boot (lombok e o proprio configuration-processor).
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor("org.projectlombok:lombok")
    // Gera META-INF/spring-configuration-metadata.json para o ApiProperty
    // (@ConfigurationProperties("api")), habilitando autocomplete/validacao
    // no application.yml pela IDE.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    testAnnotationProcessor("org.projectlombok:lombok")
}

// Resolve as mesmas coordenadas de cveOverrideCoordinates numa configuration
// isolada, so com o BOM do Spring Boot e sem as constraints acima, pra saber
// que versao o BOM resolveria sozinho hoje - sem isso, nunca ficaria obvio
// que um override virou redundante depois de um bump no BOM.
//
// detachedConfiguration, nao configurations.create: uma configuration normal
// do projeto e varrida pelo Automatic Dependency Submission junto com todas
// as outras, entao a versao vulneravel que ela resolve de proposito (BOM
// puro, sem o override) seria submetida ao grafo de dependencias do GitHub
// como se o projeto realmente dependesse dela - gerou alertas novos e falsos
// (#19-25) na primeira vez que isso rodou. Uma configuration detached nunca
// entra no container de configurations do projeto, entao fica fora da
// varredura.
val cveOverridesBomOnly: Configuration = configurations.detachedConfiguration(
    dependencies.platform(SpringBootPlugin.BOM_COORDINATES),
    *cveOverrideCoordinates.map { coordinate -> dependencies.create(coordinate) }.toTypedArray(),
)

// Falha com instrucao de qual constraint apagar assim que o BOM do Spring
// Boot alcancar (ou passar) a versao pinada - ver ADR 0016 (mesmo espirito
// de "nao deixar uma excecao acumular poeira" das outras decisoes de
// proporcionalidade deste projeto). A versao pinada e lida direto das
// constraints literais declaradas acima (nunca duplicada num Map, pelo
// mesmo motivo do comentario ali). Rodado semanalmente junto com o CodeQL
// (.github/workflows/codeql.yml, cron de segunda as 06h) em vez de ganhar
// um workflow dedicado so pra isso.
val checkCveOverrides = tasks.register("checkCveOverrides") {
    group = "verification"
    description = "Falha se o BOM do Spring Boot ja alcancou a versao de alguma constraint de CVE (pode ser removida)."
    doLast {
        val pinnedVersions = configurations.getByName("implementation").dependencyConstraints
            .filter { "${it.group}:${it.name}" in cveOverrideCoordinates }
            .associate { "${it.group}:${it.name}" to it.version!! }
        val bomResolved = cveOverridesBomOnly.resolvedConfiguration.resolvedArtifacts
            .associate { "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}" to it.moduleVersion.id.version }
        val redundant = pinnedVersions.filter { (coordinate, pinned) ->
            val resolved = bomResolved[coordinate] ?: return@filter false
            compareVersions(resolved, pinned) >= 0
        }
        if (redundant.isNotEmpty()) {
            val detalhe = redundant.entries.joinToString("\n") { (coordinate, pinned) ->
                "  - $coordinate: BOM ja resolve ${bomResolved[coordinate]} (pinado em $pinned) - remova a constraint"
            }
            throw GradleException("Constraints de CVE redundantes, o BOM do Spring Boot ja alcancou:\n$detalhe")
        }
    }
}

// Comparacao numerica simples (1.2.3 vs 1.2.10): suficiente pras versoes
// puramente numericas do Tomcat/Jackson hoje em cveOverrides; nao trata
// qualificadores tipo "-RC1" - se um dia aparecer, o parse cai pra 0 e o
// task so fica mais conservador (prefere nao sinalizar remocao em vez de
// sinalizar errado).
fun compareVersions(a: String, b: String): Int {
    val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
    val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(partsA.size, partsB.size)) {
        val cmp = (partsA.getOrElse(i) { 0 }).compareTo(partsB.getOrElse(i) { 0 })
        if (cmp != 0) return cmp
    }
    return 0
}

// Agente do Byte Buddy para o inline-mock-maker do Mockito no Java 21+ - sem
// isso, o Mockito se auto-anexa dinamicamente e emite o aviso "Dynamic
// loading of agents will be disallowed by default in a future release" em
// todo `gradlew test`. Configuration dedicada so pra resolver o jar e expor
// o caminho pro -javaagent.
val byteBuddyAgent: Configuration = configurations.create("byteBuddyAgent")

dependencies {
    // Configuration isolada nao estende de implementation, entao o platform()
    // precisa ser importado aqui de novo pra resolver a versao gerenciada
    // pelo BOM do Spring Boot.
    byteBuddyAgent(platform(SpringBootPlugin.BOM_COORDINATES))
    byteBuddyAgent("net.bytebuddy:byte-buddy-agent")
}

// --- source set de integration test (equivalente ao Failsafe do Maven) ---
// Testes *IT (ex: EstadoRepositoryIT, contra Postgres real via Testcontainers)
// rodam aqui, nao no `test` - mantem `gradlew test` rapido (so unit/slice) e
// `gradlew check` cobrindo integracao.
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations.getByName("integrationTestImplementation")
    .extendsFrom(configurations.testImplementation.get())
configurations.getByName("integrationTestRuntimeOnly")
    .extendsFrom(configurations.testRuntimeOnly.get())
configurations.getByName("integrationTestAnnotationProcessor")
    .extendsFrom(configurations.testAnnotationProcessor.get())

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Roda testes *IT (ex: EstadoRepositoryIT) contra Postgres real via Testcontainers."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    jvmArgs("-javaagent:${byteBuddyAgent.singleFile.absolutePath}")
    // Nao instrumentado pelo JaCoCo - cobertura reportada reflete so os
    // testes unitarios, igual ao setup do Maven (jacocoArgLine so era
    // injetado no Surefire, nunca no Failsafe).
    extensions.getByType<JacocoTaskExtension>().isEnabled = false
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-javaagent:${byteBuddyAgent.singleFile.absolutePath}")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// "gradlew check" ~ "mvn verify": unit + IT + relatorio de cobertura.
tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestReport)
}

springBoot {
    buildInfo {
        properties {
            additional.set(
                mapOf("commit" to (project.findProperty("gitCommit") as String? ?: "unknown"))
            )
        }
    }
}

// Native image (Dockerfile.native): so a task nativeCompile usa isto. -Ob
// (compilacao rapida, binario menos otimizado) so quando pedido, pra CI.
graalvmNative {
    binaries {
        named("main") {
            if (project.hasProperty("nativeQuick")) {
                buildArgs.add("-Ob")
            }
        }
    }
}
