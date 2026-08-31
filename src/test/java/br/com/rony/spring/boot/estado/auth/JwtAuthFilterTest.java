package br.com.rony.spring.boot.estado.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class JwtAuthFilterTest {

    private static final String HEADER = "Authorization";

    @Mock
    JwtService jwtService;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    public void setUp() {
        filter = new JwtAuthFilter(jwtService);
    }

    @AfterEach
    public void limpaContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void headerAusenteSeguesSemPopularContexto() throws ServletException, IOException {
        when(request.getHeader(HEADER)).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    public void tokenValidoPopulaContextoComOUsername() throws ServletException, IOException {
        when(request.getHeader(HEADER)).thenReturn("Bearer token-valido");
        when(jwtService.validateAndGetSubject("token-valido")).thenReturn("admin");

        filter.doFilter(request, response, chain);

        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(request, response);
    }

    @Test
    public void tokenInvalidoSeguesSemPopularContexto() throws ServletException, IOException {
        when(request.getHeader(HEADER)).thenReturn("Bearer token-invalido");
        when(jwtService.validateAndGetSubject("token-invalido")).thenThrow(new JwtException("invalido"));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    // WeakKeyException (segredo HMAC curto demais) e subtipo de JwtException
    // (io.jsonwebtoken.security: WeakKeyException -> InvalidKeyException ->
    // KeyException -> SecurityException -> JwtException), entao ja cai no
    // catch existente - misconfiguracao de JWT_SECRET nao deve virar 500.
    @Test
    public void segredoFracoNaValidacaoSeguesSemPopularContextoENaoPropaga() throws ServletException, IOException {
        when(request.getHeader(HEADER)).thenReturn("Bearer token-qualquer");
        when(jwtService.validateAndGetSubject("token-qualquer")).thenThrow(new WeakKeyException("segredo fraco"));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    public void headerSemPrefixoBearerSeguesSemPopularContexto() throws ServletException, IOException {
        when(request.getHeader(HEADER)).thenReturn("token-sem-prefixo");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
