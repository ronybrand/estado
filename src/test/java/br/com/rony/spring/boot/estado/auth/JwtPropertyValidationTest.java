package br.com.rony.spring.boot.estado.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// jwt.expiration-minutes<=0 emitiria token que ja nasce expirado - deve
// falhar rapido na inicializacao (fail-fast) em vez de deixar o problema
// aparecer so quando alguem tentar logar em producao (ver ADR 0017).
public class JwtPropertyValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(JwtPropertyEnabler.class);

    @Test
    public void expirationMinutesZeroFalhaNaInicializacao() {
        runner.withPropertyValues("jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes-de-tamanho",
                "jwt.expiration-minutes=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    public void expirationMinutesNegativoFalhaNaInicializacao() {
        runner.withPropertyValues("jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes-de-tamanho",
                "jwt.expiration-minutes=-5")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    public void expirationMinutesPositivoInicializaComSucesso() {
        runner.withPropertyValues("jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes-de-tamanho",
                "jwt.expiration-minutes=60")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @EnableConfigurationProperties(JwtProperty.class)
    static class JwtPropertyEnabler {
    }
}
