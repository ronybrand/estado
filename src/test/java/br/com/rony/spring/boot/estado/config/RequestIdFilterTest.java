package br.com.rony.spring.boot.estado.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RequestIdFilterTest {

	private static final String HEADER = "X-Request-Id";
	private static final String MDC_KEY = "requestId";

	private final RequestIdFilter filter = new RequestIdFilter();

	@Mock
	HttpServletRequest request;

	@Mock
	HttpServletResponse response;

	@Mock
	FilterChain chain;

	@Test
	public void poeNoMdcDuranteACadeiaERemoveDepoisDoFiltro() throws ServletException, IOException {
		when(request.getHeader(HEADER)).thenReturn(null);
		AtomicReference<String> mdcDuranteCadeia = new AtomicReference<>();
		doAnswer(invocation -> {
			mdcDuranteCadeia.set(MDC.get(MDC_KEY));
			return null;
		}).when(chain).doFilter(request, response);

		filter.doFilter(request, response, chain);

		assertNotNull(mdcDuranteCadeia.get());
		assertNull(MDC.get(MDC_KEY));
	}

	@Test
	public void ecoaUuidGeradoComoHeaderDeResposta() throws ServletException, IOException {
		when(request.getHeader(HEADER)).thenReturn(null);

		filter.doFilter(request, response, chain);

		verify(response).setHeader(eq(HEADER), anyString());
	}

	@Test
	public void reaproveitaUuidValidoRecebidoNoHeaderDeEntrada() throws ServletException, IOException {
		String uuid = "11111111-1111-1111-1111-111111111111";
		when(request.getHeader(HEADER)).thenReturn(uuid);

		filter.doFilter(request, response, chain);

		verify(response).setHeader(HEADER, uuid);
	}

	@Test
	public void geraUuidNovoQuandoHeaderDeEntradaNaoEUuidValido() throws ServletException, IOException {
		when(request.getHeader(HEADER)).thenReturn("nao-e-uuid");

		filter.doFilter(request, response, chain);

		verify(response, never()).setHeader(HEADER, "nao-e-uuid");
		verify(response).setHeader(eq(HEADER), anyString());
	}
}
