package br.com.rony.spring.boot.estado.property;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Cadeia de timeouts do /ask: Angular (60 s) > este backend > ai-agent (ate
// duas tentativas de 15 s ao Gemini). O backend precisa esperar mais do que o
// pior caso do agent, senao desiste antes de o agent responder o erro tratavel.
class AskApiPropertyTimeoutsTest {

    private static final long TIMEOUT_DO_ANGULAR_MS = 60_000;
    private static final long PIOR_CASO_DO_AGENT_MS = 30_000;

    @Test
    void valorPadraoDaClasseEspera45Segundos() {
        assertThat(new AskApiProperty().getReadTimeoutMs()).isEqualTo(45_000);
    }

    @Test
    void applicationYmlConfiguraOMesmoReadTimeout() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Habilitador.class)
                .withPropertyValues("ASK_API_BASE_URL=http://localhost:8081", "ASK_API_KEY=teste")
                .run(contexto -> assertThat(contexto.getBean(AskApiProperty.class).getReadTimeoutMs())
                        .isEqualTo(45_000));
    }

    @Test
    void readTimeoutFicaEntreOPiorCasoDoAgentEOTimeoutDoAngular() {
        assertThat(new AskApiProperty().getReadTimeoutMs())
                .isGreaterThan(PIOR_CASO_DO_AGENT_MS)
                .isLessThan(TIMEOUT_DO_ANGULAR_MS);
    }

    @EnableConfigurationProperties(AskApiProperty.class)
    static class Habilitador {
    }
}
