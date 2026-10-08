package br.com.rony.spring.boot.estado.property;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// readTimeoutMs/connectTimeoutMs<=0 configurariam um RestClient que falha
// toda chamada ao estado-ai-agent na hora - deve falhar rapido na
// inicializacao, mesmo padrao ja usado em JwtProperty/PaginationProperty.
class AskApiPropertyValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(AskApiPropertyEnabler.class);

    @Test
    void readTimeoutMsZeroFalhaNaInicializacao() {
        runner.withPropertyValues(
                        "ask-api.base-url=http://localhost:8081",
                        "ask-api.api-key=teste",
                        "ask-api.read-timeout-ms=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void connectTimeoutMsNegativoFalhaNaInicializacao() {
        runner.withPropertyValues(
                        "ask-api.base-url=http://localhost:8081",
                        "ask-api.api-key=teste",
                        "ask-api.connect-timeout-ms=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void valoresPositivosInicializamComSucesso() {
        runner.withPropertyValues(
                        "ask-api.base-url=http://localhost:8081",
                        "ask-api.api-key=teste",
                        "ask-api.connect-timeout-ms=3000",
                        "ask-api.read-timeout-ms=25000")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @EnableConfigurationProperties(AskApiProperty.class)
    static class AskApiPropertyEnabler {
    }
}
