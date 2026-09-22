package br.com.rony.spring.boot.estado.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Sem default pra baseUrl/apiKey (mesmo padrao de admin/jwt): falha rapido no
// boot em vez de rodar silenciosamente sem conseguir falar com o
// estado-ai-agent, ou pior, com uma api-key vazia.
@Getter
@Setter
@Validated
@ConfigurationProperties("ask-api")
public class AskApiProperty {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String apiKey;

}
