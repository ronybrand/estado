package br.com.rony.spring.boot.estado.ask;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.com.rony.spring.boot.estado.property.AskApiProperty;

// Bean explicito: a autoconfiguracao do Spring Boot (RestClientAutoConfiguration)
// nao estava disponibilizando um RestClient.Builder neste projeto (achado em
// producao - AskProxyService falhava no boot com UnsatisfiedDependencyException,
// "No qualifying bean of type RestClient$Builder"), provavelmente por faltar
// um starter de connector HTTP explicito no classpath.
//
// @EnableConfigurationProperties(AskApiProperty.class): sem @ConfigurationPropertiesScan
// global no projeto (mesmo padrao de ApiProperty/JwtProperty, habilitadas em
// WebConfig/SecurityConfig), uma classe @ConfigurationProperties precisa ser
// habilitada explicitamente em algum @Configuration - faltou aqui, segundo
// bug pego pelo mesmo AppContextLoadsIT que pegou o do RestClient.Builder.
@Configuration
@EnableConfigurationProperties(AskApiProperty.class)
public class AskProxyClientConfig {

    // Timeout explicito (ver AskApiProperty) - sem isso, uma resposta lenta do
    // estado-ai-agent ficava presa indefinidamente, prendendo uma thread do
    // servlet deste backend (que tambem serve o CRUD /estado), achado numa
    // revisao integrada entre os 3 repos do ecossistema.
    @Bean
    public RestClient.Builder restClientBuilder(AskApiProperty askApiProperty) {
        HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(
                Duration.ofMillis(askApiProperty.getConnectTimeoutMs()),
                Duration.ofMillis(askApiProperty.getReadTimeoutMs()));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return RestClient.builder().requestFactory(requestFactory);
    }
}
