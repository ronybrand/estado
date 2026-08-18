package br.com.rony.spring.boot.estado.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ActuatorNoCacheFilterTest {

	private final ActuatorNoCacheFilter filter = new ActuatorNoCacheFilter();

	@Mock
	HttpServletRequest request;

	@Mock
	HttpServletResponse response;

	@Mock
	FilterChain chain;

	@Test
	public void definePorCacheControlNoStorePraRotasDeActuator() throws ServletException, IOException {
		when(request.getRequestURI()).thenReturn("/actuator/info");

		filter.doFilter(request, response, chain);

		verify(response).setHeader("Cache-Control", "no-store");
	}

	@Test
	public void naoDefineCacheControlPraRotasQueNaoSaoDeActuator() throws ServletException, IOException {
		when(request.getRequestURI()).thenReturn("/estado/");

		filter.doFilter(request, response, chain);

		verify(response, never()).setHeader(eq("Cache-Control"), eq("no-store"));
	}
}
