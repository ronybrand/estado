package br.com.rony.spring.boot.estado.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import br.com.rony.spring.boot.estado.property.RateLimitProperty;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RateLimitFilterTest {

    private RateLimitFilter filter;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain chain;

    @BeforeEach
    public void setUp() {
        RateLimitProperty property = new RateLimitProperty();
        property.setCapacidade(2);
        property.setJanelaSegundos(60);
        filter = new RateLimitFilter(property);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    public void deixaPassarRequisicoesDentroDaCapacidade() throws ServletException, IOException {
        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);

        verify(chain, times(2)).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    public void bloqueiaComQuandoEstourarCapacidadeDaJanela() throws ServletException, IOException {
        StringWriter corpoEscrito = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(corpoEscrito));

        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);

        verify(chain, times(2)).doFilter(request, response);
        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        assertTrue(corpoEscrito.toString().contains("Muitas requisicoes"));
    }

    @Test
    public void contaBucketsSeparadosPorIp() throws ServletException, IOException {
        HttpServletRequest outroRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(outroRequest.getRemoteAddr()).thenReturn("10.0.0.1");

        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);
        filter.doFilter(outroRequest, response, chain);

        verify(chain, times(3)).doFilter(org.mockito.Mockito.any(), org.mockito.Mockito.eq(response));
        verify(response, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
