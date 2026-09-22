package br.com.rony.spring.boot.estado.ask;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import br.com.rony.spring.boot.estado.property.AskApiProperty;

// Bean explicito: a autoconfiguracao do Spring Boot (RestClientAutoConfiguration)
// nao estava disponibilizando um RestClient.Builder neste projeto (achado em
// producao - AskProxyService falhava no boot com UnsatisfiedDependencyException,
// "No qualifying bean of type RestClient$Builder"), provavelmente por faltar
// um starter de connector HTTP explicito no classpath. RestClient.builder()
// sem customizacao usa o connector default da JDK, suficiente pro proxy do
// /ask - nao precisamos de nenhum connector especifico (Apache/Reactor Netty).
//
// @EnableConfigurationProperties(AskApiProperty.class): sem @ConfigurationPropertiesScan
// global no projeto (mesmo padrao de ApiProperty/JwtProperty, habilitadas em
// WebConfig/SecurityConfig), uma classe @ConfigurationProperties precisa ser
// habilitada explicitamente em algum @Configuration - faltou aqui, segundo
// bug pego pelo mesmo AppContextLoadsIT que pegou o do RestClient.Builder.
@Configuration
@EnableConfigurationProperties(AskApiProperty.class)
public class AskProxyClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
