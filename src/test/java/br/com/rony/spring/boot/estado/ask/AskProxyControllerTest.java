package br.com.rony.spring.boot.estado.ask;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.rony.spring.boot.estado.auth.JwtService;
import br.com.rony.spring.boot.estado.auth.SecurityConfig;

// @Import(SecurityConfig.class): mesmo motivo do AuthControllerTest - e a
// unica fonte de AdminProperty/JwtProperty, e o filtro precisa de JwtService
// mockado pra existir como bean. POST /ask e permitAll (ver SecurityConfig),
// entao o filterChain nao interfere no resultado destes testes.
@WebMvcTest(AskProxyController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"admin.username=admin", "admin.password-hash=hash-de-teste",
        "jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes", "jwt.expiration-minutes=60",
        "rate-limit.login-capacidade=100"})
class AskProxyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AskProxyService askProxyService;

    @MockitoBean
    JwtService jwtService;

    @Test
    void deveRepassarPerguntaSemExigirAutenticacaoEDevolverResposta() throws Exception {
        when(askProxyService.ask(any(), any())).thenReturn(new AskProxyResponseDto("Curitiba"));

        mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Qual a capital do Parana?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Curitiba"));
    }

    // O rate limit do estado-ai-agent so continua valendo por usuario real (e
    // nao pelo IP unico deste backend) se o IP do cliente que chegou aqui for
    // repassado ao service - e isso que este teste protege contra regressao.
    @Test
    void deveRepassarIpRemotoDoClienteParaOService() throws Exception {
        when(askProxyService.ask(any(), any())).thenReturn(new AskProxyResponseDto("Curitiba"));

        mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Qual a capital do Parana?\"}")
                .with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                }))
                .andExpect(status().isOk());

        verify(askProxyService).ask(eq(new AskProxyRequestDto("Qual a capital do Parana?")), eq("203.0.113.5"));
    }

    @Test
    void deveRetornar400QuandoPerguntaEstiverEmBranco() throws Exception {
        mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoPerguntaExcederTamanhoMaximo() throws Exception {
        String perguntaMuitoLonga = "a".repeat(1001);

        mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + perguntaMuitoLonga + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // Mesmo achado do AuthControllerTest: WebConfig.addCorsMappings mapeava so
    // /estado/** e /auth/**, entao um cliente cross-origin de verdade (nao via
    // rewrite same-origin) tinha /ask bloqueado mesmo com a origem na allowlist.
    @Test
    void perguntaComOrigemPermitidaEcoaOAccessControlAllowOrigin() throws Exception {
        when(askProxyService.ask(any(), any())).thenReturn(new AskProxyResponseDto("Curitiba"));

        mockMvc.perform(post("/ask")
                .header("Origin", "http://localhost:8000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Qual a capital do Parana?\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8000"));
    }
}
