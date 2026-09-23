package br.com.rony.spring.boot.estado.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

    private static final String REQUEST_ID = "8f14e45f-ceea-4b78-8b0f-000000000001";

    @Test
    void deveRepassarPerguntaComApiKeyIpDoClienteERequestIdEDevolverResposta() {
        server.expect(requestTo(BASE_URL + "/ask"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "chave-de-teste"))
                .andExpect(header("X-Forwarded-For", CLIENT_IP))
                .andExpect(header("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.question").value("Qual a capital do Parana?"))
                .andRespond(withSuccess("{\"answer\":\"Curitiba\"}", MediaType.APPLICATION_JSON));

        AskProxyResponseDto response = service.ask(
                new AskProxyRequestDto("Qual a capital do Parana?"), CLIENT_IP, REQUEST_ID);

        assertThat(response.answer()).isEqualTo("Curitiba");
    }

    @Test
    void deveLancarAskUpstreamExceptionCom502QuandoOUpstreamFalhaComErroDeServidor() {
        server.expect(requestTo(BASE_URL + "/ask")).andRespond(withServerError());
        AskProxyRequestDto request = new AskProxyRequestDto("Qual a capital do Parana?");

        assertThatThrownBy(callAsk(request))
                .isInstanceOf(AskUpstreamException.class)
                .satisfies(ex -> assertThat(((AskUpstreamException) ex).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void deveLancarAskUpstreamExceptionCom502QuandoConexaoFalha() {
        server.expect(requestTo(BASE_URL + "/ask")).andRespond(request -> {
            throw new java.io.IOException("connection refused");
        });
        AskProxyRequestDto request = new AskProxyRequestDto("Qual a capital do Parana?");

        assertThatThrownBy(callAsk(request))
                .isInstanceOf(AskUpstreamException.class)
                .satisfies(ex -> assertThat(((AskUpstreamException) ex).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void devePreservarStatus429EMensagemQuandoUpstreamAplicaRateLimit() {
        // O rate limit e do proprio estado-ai-agent (por usuario final, via
        // X-Forwarded-For), nao deste backend - sem isso, o usuario ve um 502
        // generico ("falha ao consultar") em vez de saber que precisa esperar.
        server.expect(requestTo(BASE_URL + "/ask")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"ASK-04\",\"message\":\"Limite de requisicoes excedido\",\"requestId\":\"" + REQUEST_ID + "\"}"));
        AskProxyRequestDto request = new AskProxyRequestDto("Qual a capital do Parana?");

        assertThatThrownBy(callAsk(request))
                .isInstanceOf(AskUpstreamException.class)
                .hasMessage("Limite de requisicoes excedido")
                .satisfies(ex -> assertThat(((AskUpstreamException) ex).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void devePreservarStatus400QuandoUpstreamRejeitaInputInvalido() {
        server.expect(requestTo(BASE_URL + "/ask")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"ASK-01\",\"message\":\"question excede o tamanho maximo\",\"requestId\":\"" + REQUEST_ID + "\"}"));
        AskProxyRequestDto request = new AskProxyRequestDto("Qual a capital do Parana?");

        assertThatThrownBy(callAsk(request))
                .isInstanceOf(AskUpstreamException.class)
                .hasMessage("question excede o tamanho maximo")
                .satisfies(ex -> assertThat(((AskUpstreamException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deveUsarMensagemGenericaQuandoCorpoDoErro429NaoTemCampoMessage() {
        server.expect(requestTo(BASE_URL + "/ask"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("nao e json"));
        AskProxyRequestDto request = new AskProxyRequestDto("Qual a capital do Parana?");

        assertThatThrownBy(callAsk(request))
                .isInstanceOf(AskUpstreamException.class)
                .satisfies(ex -> assertThat(((AskUpstreamException) ex).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private ThrowingCallable callAsk(AskProxyRequestDto request) {
        return () -> service.ask(request, CLIENT_IP, REQUEST_ID);
    }
}
