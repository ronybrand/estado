package br.com.rony.spring.boot.estado.property;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// pagination.max-size<=0 faria PageableHandlerMethodArgumentResolverCustomizer
// (WebConfig) clampar toda pagina pra um tamanho invalido - deve falhar rapido
// na inicializacao, mesmo padrao ja usado em JwtProperty (ver ADR 0017).
public class PaginationPropertyValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(PaginationPropertyEnabler.class);

    @Test
    public void maxSizeZeroFalhaNaInicializacao() {
        runner.withPropertyValues("pagination.max-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    public void maxSizeNegativoFalhaNaInicializacao() {
        runner.withPropertyValues("pagination.max-size=-10")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    public void maxSizePositivoInicializaComSucesso() {
        runner.withPropertyValues("pagination.max-size=100")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @EnableConfigurationProperties(PaginationProperty.class)
    static class PaginationPropertyEnabler {
    }
}
