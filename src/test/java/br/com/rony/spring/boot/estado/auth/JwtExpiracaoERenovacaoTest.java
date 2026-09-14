package br.com.rony.spring.boot.estado.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

// JwtServiceTest cobre expiracao isolada (so o JwtService); JwtAuthFilterTest
// cobre o filtro isolado (com JwtService mockado). Nenhum dos dois provava o
// par JwtService+JwtAuthFilter reais wireados juntos, que e o que roda em
// producao. Aqui os dois sao instanciados de verdade (sem mocks, sem contexto
// Spring) pra fechar esse gap: token expirado e rejeitado pelo filtro real, e
// "renovacao" - que no sistema so existe como reautenticacao via /auth/login,
// nao ha refresh token (ADR 0017, ver secao "Authentication (JWT)" do README)
// - e simulada emitindo um novo token pelo mesmo JwtService, exatamente como
// AuthController#login faz, e provando que ele passa no mesmo filtro.
public class JwtExpiracaoERenovacaoTest {

    private static final String SEGREDO = "segredo-de-teste-com-pelo-menos-32-bytes";

    @AfterEach
    public void limpaContexto() {
        SecurityContextHolder.clearContext();
    }

    private JwtService jwtService(long expirationMinutes) {
        JwtProperty property = new JwtProperty();
        property.setSecret(SEGREDO);
        property.setExpirationMinutes(expirationMinutes);
        return new JwtService(property);
    }

    private void passaPeloFiltro(JwtAuthFilter filtro, String token, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filtro.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    public void tokenExpiradoNaoPopulaContextoAoPassarPeloFiltroReal() throws Exception {
        // expirationMinutes=-1 emite um token com "exp" no passado (mesma tecnica de
        // JwtServiceTest), sem precisar de Thread.sleep/mock de relogio. JwtProperty
        // real exige expirationMinutes > 0 (@Positive) - a aplicacao nunca emitiria um
        // token assim sozinha; aqui simula um token que expirou apos ser emitido.
        String tokenExpirado = jwtService(-1).issueToken("admin");
        JwtAuthFilter filtro = new JwtAuthFilter(jwtService(60));
        FilterChain chain = mock(FilterChain.class);

        passaPeloFiltro(filtro, tokenExpirado, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(any(), any());
    }

    @Test
    public void tokenValidoEmitidoPeloJwtServiceRealPopulaContextoAoPassarPeloFiltroReal() throws Exception {
        JwtService servico = jwtService(60);
        JwtAuthFilter filtro = new JwtAuthFilter(servico);
        String token = servico.issueToken("admin");
        FilterChain chain = mock(FilterChain.class);

        passaPeloFiltro(filtro, token, chain);

        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(any(), any());
    }

    @Test
    public void aposTokenExpiradoReautenticacaoEmiteNovoTokenQuePassaNoMesmoFiltro() throws Exception {
        JwtService servico = jwtService(60);
        JwtAuthFilter filtro = new JwtAuthFilter(servico);

        String tokenExpirado = jwtService(-1).issueToken("admin");
        passaPeloFiltro(filtro, tokenExpirado, mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        String novoToken = servico.issueToken("admin");
        passaPeloFiltro(filtro, novoToken, mock(FilterChain.class));

        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}
