package br.com.rony.spring.boot.estado.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// @Import(SecurityConfig.class): e a unica fonte de AdminProperty/JwtProperty
// (habilitadas via @EnableConfigurationProperties la, nao aqui) - sem isso o
// construtor de AuthController fica sem bean pra injetar. /auth/login e
// permitAll, entao o filterChain nao interfere no resultado destes testes.
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"admin.username=admin", "admin.password-hash=hash-de-teste",
        "jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes", "jwt.expiration-minutes=60"})
public class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    PasswordEncoder passwordEncoder;

    @Test
    public void loginComCredenciaisValidasRetorna200ComToken() throws Exception {
        when(passwordEncoder.matches("senha-correta", "hash-de-teste")).thenReturn(true);
        when(jwtService.issueToken("admin")).thenReturn("token-emitido");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"senha-correta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-emitido"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }

    @Test
    public void loginComSenhaErradaRetorna401() throws Exception {
        when(passwordEncoder.matches("senha-errada", "hash-de-teste")).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"senha-errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    public void loginComUsuarioErradoRetorna401() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"outro\",\"password\":\"qualquer\"}"))
                .andExpect(status().isUnauthorized());
    }

    // Usuario errado nao pode ser rejeitado antes de invocar o BCrypt: um
    // short-circuit no username criaria um timing side-channel que revela o
    // username valido pela diferenca de latencia entre as duas respostas 401.
    @Test
    public void loginComUsuarioErradoAindaAssimInvocaBcryptParaEvitarTimingSideChannel() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"outro\",\"password\":\"qualquer\"}"))
                .andExpect(status().isUnauthorized());

        verify(passwordEncoder).matches(anyString(), anyString());
    }

    @Test
    public void loginComCamposEmBrancoRetorna400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
