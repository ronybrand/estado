package br.com.rony.spring.boot.estado.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.rony.spring.boot.estado.property.AskApiProperty;

class AskProxyServiceTest {

    private static final String BASE_URL = "http://ai-agent.internal";
    private static final String CLIENT_IP = "203.0.113.5";

    private MockRestServiceServer server;
    private AskProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        AskApiProperty property = new AskApiProperty();
        property.setBaseUrl(BASE_URL);
        property.setApiKey("chave-de-teste");

        service = new AskProxyService(builder, property);
    }

    @Test
    void deveRepassarPerguntaComApiKeyEIpDoClienteEDevolverResposta() {
        server.expect(requestTo(BASE_URL + "/ask"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "chave-de-teste"))
                .andExpect(header("X-Forwarded-For", CLIENT_IP))
                .andExpect(jsonPath("$.question").value("Qual a capital do Parana?"))
                .andRespond(withSuccess("{\"answer\":\"Curitiba\"}", MediaType.APPLICATION_JSON));

        AskProxyResponseDto response = service.ask(new AskProxyRequestDto("Qual a capital do Parana?"), CLIENT_IP);

        assertThat(response.answer()).isEqualTo("Curitiba");
    }

    @Test
    void deveLancarAskUpstreamExceptionQuandoOUpstreamFalha() {
        server.expect(requestTo(BASE_URL + "/ask")).andRespond(withServerError());
        AskProxyRequestDto request = new AskProxyRequestDto("Qual a capital do Parana?");

        assertThatThrownBy(() -> service.ask(request, CLIENT_IP))
                .isInstanceOf(AskUpstreamException.class);
    }
}
