package br.com.rony.spring.boot.estado.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import br.com.rony.spring.boot.estado.property.ApiProperty;

public class WebConfigTest {

	private final WebConfig webConfig = new WebConfig(new ApiProperty());

	@Test
	public void actuatorNoCacheFilterEscopadoSoPraRotasDeActuator() {
		// achado de code review: o filtro escopava via startsWith em runtime,
		// rodando pra toda requisicao da aplicacao - o escopo agora e
		// declarativo, via FilterRegistrationBean, e o servlet container nem
		// invoca o filtro fora de /actuator/*.
		FilterRegistrationBean<ActuatorNoCacheFilter> registration = webConfig.actuatorNoCacheFilter();

		assertEquals(1, registration.getUrlPatterns().size());
		assertTrue(registration.getUrlPatterns().contains("/actuator/*"));
	}
}
