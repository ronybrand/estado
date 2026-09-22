package br.com.rony.spring.boot.estado;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Sobe o contexto Spring INTEIRO (nao um slice) contra um Postgres real via
// Testcontainers - achado em producao: AskProxyService dependia de
// RestClient.Builder, que nenhum slice (@WebMvcTest com @MockitoBean nos
// collaborators) e nenhum unit test exercitava a montagem real do bean. O
// deploy so falhou no boot em producao (rede de seguranca do rolling swap
// evitou o downtime, mas o bug so foi pego la). Este teste roda a mesma
// montagem de beans que a app real faz no boot, incluindo os que exigem
// as env vars obrigatorias (ASK_API_KEY etc.) - sem elas, falha aqui, nao
// so em producao.
@SpringBootTest
@TestPropertySource(properties = {
        "admin.username=admin", "admin.password-hash=hash-de-teste",
        "jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes", "jwt.expiration-minutes=60",
        "ask-api.base-url=http://ai-agent.internal", "ask-api.api-key=chave-de-teste"})
@Testcontainers
class AppContextLoadsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void contextoSobeComTodosOsBeansMontados() {
        // Sem asserts alem do proprio @SpringBootTest: se algum bean nao
        // conseguir ser criado (dependencia faltando, property obrigatoria
        // ausente), o teste falha na inicializacao do contexto.
    }
}
