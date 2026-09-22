package br.com.rony.spring.boot.estado.ask;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.rony.spring.boot.estado.property.AskApiProperty;

// Repassa a pergunta ao estado-ai-agent server-to-server: a ASK_API_KEY vive
// so aqui, nunca no Angular (ver ADR do BFF). Encaminha o IP real do cliente
// via X-Forwarded-For para que o rate limiter do proprio ai-agent continue
// limitando por usuario final, nao pelo IP unico deste backend.
@Service
public class AskProxyService {

    private final RestClient restClient;
    private final AskApiProperty askApiProperty;

    public AskProxyService(RestClient.Builder restClientBuilder, AskApiProperty askApiProperty) {
        this.restClient = restClientBuilder.baseUrl(askApiProperty.getBaseUrl()).build();
        this.askApiProperty = askApiProperty;
    }

    public AskProxyResponseDto ask(AskProxyRequestDto request, String clientIp) {
        try {
            return restClient.post()
                    .uri("/ask")
                    .header("X-API-Key", askApiProperty.getApiKey())
                    .header("X-Forwarded-For", clientIp)
                    .body(request)
                    .retrieve()
                    .body(AskProxyResponseDto.class);
        } catch (RestClientException e) {
            throw new AskUpstreamException("Falha ao consultar o estado-ai-agent", e);
        }
    }
}
