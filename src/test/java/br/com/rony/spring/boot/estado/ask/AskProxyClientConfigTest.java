package br.com.rony.spring.boot.estado.ask;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import br.com.rony.spring.boot.estado.property.AskApiProperty;

class AskProxyClientConfigTest {

    private final AskProxyClientConfig config = new AskProxyClientConfig();

    @Test
    void httpClientSettingsUsaOsTimeoutsConfiguradosEmAskApiProperty() {
        AskApiProperty askApiProperty = new AskApiProperty();
        askApiProperty.setConnectTimeoutMs(1234);
        askApiProperty.setReadTimeoutMs(5678);

        var settings = AskProxyClientConfig.httpClientSettings(askApiProperty);

        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofMillis(1234));
        assertThat(settings.readTimeout()).isEqualTo(Duration.ofMillis(5678));
    }

    @Test
    void restClientBuilderConstroiUmBuilderUtilizavel() {
        AskApiProperty askApiProperty = new AskApiProperty();
        askApiProperty.setConnectTimeoutMs(3000);
        askApiProperty.setReadTimeoutMs(25000);

        RestClient.Builder builder = config.restClientBuilder(askApiProperty);

        assertThat(builder).isNotNull();
        assertThat(builder.baseUrl("http://localhost:8081").build()).isNotNull();
    }
}
