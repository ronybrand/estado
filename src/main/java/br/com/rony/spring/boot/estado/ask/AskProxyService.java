package br.com.rony.spring.boot.estado.ask;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.rony.spring.boot.estado.property.AskApiProperty;

// Repassa a pergunta ao estado-ai-agent server-to-server: a ASK_API_KEY vive
// so aqui, nunca no Angular (ver ADR do BFF). Encaminha o IP real do cliente
// via X-Forwarded-For para que o rate limiter do proprio ai-agent continue
// limitando por usuario final, nao pelo IP unico deste backend. Encaminha
// tambem o X-Request-Id ja gerado por este backend (RequestIdFilter) para
// que o mesmo id correlacione o log das duas pontas da mesma requisicao.
@Service
public class AskProxyService {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String GENERIC_UPSTREAM_FAILURE_MESSAGE = "Falha ao consultar o estado-ai-agent";

    // Status do estado-ai-agent que fazem sentido repassar ao cliente final
    // em vez de mascarar como falha generica de upstream (502): 429 e o caso
    // real e esperado (rate limit por usuario final, ver X-Forwarded-For
    // acima); 400 e defensivo (a validacao aqui em AskProxyRequestDto ja
    // espelha a do estado-ai-agent, mas nao ha garantia de nunca divergir).
    // Qualquer outro status do upstream (401/403 por chave mal configurada
    // neste backend, 5xx) continua generico: nao e culpa do usuario final,
    // e nao deveria ser apresentado como se fosse.
    private static final Set<HttpStatus> PASSTHROUGH_STATUSES = Set.of(HttpStatus.BAD_REQUEST, HttpStatus.TOO_MANY_REQUESTS);

    private final RestClient restClient;
    private final AskApiProperty askApiProperty;
    // new ObjectMapper() direto, nao injetado: o Boot 4 deste projeto so
    // disponibiliza um bean tools.jackson.databind.ObjectMapper (Jackson 3)
    // via autoconfig, entao um construtor pedindo o tipo Jackson 2 (usado
    // aqui e no resto do projeto) quebra o boot com NoSuchBeanDefinitionException
    // - mesmo motivo documentado em RateLimitFilter.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AskProxyService(RestClient.Builder restClientBuilder, AskApiProperty askApiProperty) {
        this.restClient = restClientBuilder.baseUrl(askApiProperty.getBaseUrl()).build();
        this.askApiProperty = askApiProperty;
    }

    public AskProxyResponseDto ask(AskProxyRequestDto request, String clientIp, String requestId) {
        try {
            return restClient.post()
                    .uri("/ask")
                    .header("X-API-Key", askApiProperty.getApiKey())
                    .header("X-Forwarded-For", clientIp)
                    .header(REQUEST_ID_HEADER, requestId)
                    .body(request)
                    .retrieve()
                    .body(AskProxyResponseDto.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw new AskUpstreamException(GENERIC_UPSTREAM_FAILURE_MESSAGE, e);
        }
    }

    private AskUpstreamException translate(RestClientResponseException e) {
        HttpStatusCode statusCode = e.getStatusCode();
        if (!(statusCode instanceof HttpStatus status) || !PASSTHROUGH_STATUSES.contains(status)) {
            return new AskUpstreamException(GENERIC_UPSTREAM_FAILURE_MESSAGE, e);
        }
        return new AskUpstreamException(extractMessage(e).orElse(GENERIC_UPSTREAM_FAILURE_MESSAGE), status, e);
    }

    private java.util.Optional<String> extractMessage(RestClientResponseException e) {
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
            JsonNode message = body.get("message");
            if (message != null && message.isTextual() && !message.asText().isBlank()) {
                return java.util.Optional.of(message.asText());
            }
        } catch (Exception parseFailure) {
            // Corpo do erro upstream nao e o JSON esperado - cai no fallback
            // generico abaixo em vez de propagar uma falha de parsing.
        }
        return java.util.Optional.empty();
    }
}
