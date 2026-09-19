package br.com.rony.spring.boot.estado.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.rony.spring.boot.estado.Estado;
import br.com.rony.spring.boot.estado.EstadoController;
import br.com.rony.spring.boot.estado.EstadoService;

// Achado durante analise comparativa com os projetos irmaos do portfolio
// (spring-order-api): SecurityConfig aqui nao tinha nenhum header de
// hardening (CSP/HSTS/X-Frame-Options/Permissions-Policy) - GET /estado/**
// e publico (nao exige token), entao o unico teto de seguranca hoje contra
// XSS/clickjacking/downgrade pra HTTP viria desses headers, que nao
// existiam.
@WebMvcTest(EstadoController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"admin.password-hash=teste", "jwt.secret=segredo-de-teste-com-32-bytes-ou-mais"})
public class SecurityHeadersTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EstadoService service;

    @MockitoBean
    JwtService jwtService;

    @BeforeEach
    void setUp() {
        Estado domain = new Estado();
        domain.setId(1L);
        domain.setNome("Santa Catarina");
        domain.setSigla("SC");
        domain.setDataHoraCadastro(LocalDateTime.now());
        when(service.getDomainById(1L)).thenReturn(domain);
    }

    @Test
    void respostaTemContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/estado/1")).andExpect(header().string("Content-Security-Policy", "default-src 'self'"));
    }

    @Test
    void respostaTemXFrameOptionsDeny() throws Exception {
        mockMvc.perform(get("/estado/1")).andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void respostaTemStrictTransportSecurity() throws Exception {
        // HSTS so e escrito em requisicao segura (HstsHeaderWriter checa request.isSecure())
        // - simula o que o Caddy entrega pro Spring quando o cliente chega via HTTPS.
        mockMvc.perform(get("/estado/1").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void respostaTemPermissionsPolicy() throws Exception {
        mockMvc.perform(get("/estado/1"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=()"));
    }

    @Test
    void statusDaRequisicaoPublicaContinua200() throws Exception {
        mockMvc.perform(get("/estado/1")).andExpect(status().isOk());
    }
}
