package br.com.rony.spring.boot.estado.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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

    // AskProxyClientConfig nao tinha NENHUM timeout antes (RestClient.builder()
    // puro) - uma resposta lenta do estado-ai-agent ficava presa indefinidamente,
    // prendendo uma thread do servlet que tambem serve o CRUD /estado (achado
    // de revisao integrada entre os 3 repos do ecossistema). read-timeout-ms
    // maior que o pior caso do ai-agent (ate duas tentativas de 15s ao Gemini,
    // principal e reserva) de proposito: da tempo do ai-agent falhar e
    // responder um erro tratavel antes deste backend desistir primeiro, e
    // menor que os 60s do Angular.
    @Positive
    private long connectTimeoutMs = 3000;

    @Positive
    private long readTimeoutMs = 45000;

}
