package br.com.rony.spring.boot.estado.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

import br.com.rony.spring.boot.estado.property.ApiProperty;
import br.com.rony.spring.boot.estado.property.PaginationProperty;

public class WebConfigTest {

    private final PaginationProperty paginationProperty = new PaginationProperty();
    private final WebConfig webConfig = new WebConfig(new ApiProperty(), paginationProperty);

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

    @Test
    public void pageableCustomizerAplicaMaxSizeConfigurado() {
        paginationProperty.setMaxSize(42);
        PageableHandlerMethodArgumentResolverCustomizer customizer = webConfig.pageableCustomizer();
        PageableHandlerMethodArgumentResolver resolver = mock(PageableHandlerMethodArgumentResolver.class);

        customizer.customize(resolver);

        verify(resolver).setMaxPageSize(42);
    }
}
