package br.com.rony.spring.boot.estado.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.rony.spring.boot.estado.property.ApiProperty;
import br.com.rony.spring.boot.estado.property.RateLimitProperty;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({ApiProperty.class, RateLimitProperty.class})
public class WebConfig implements WebMvcConfigurer {

	private final ApiProperty apiProperty;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/estado/**")
				.allowedOrigins(apiProperty.getOriginPermitida().toArray(new String[0]))
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("Authorization", "Content-Type", "Accept")
				.allowCredentials(true)
				.maxAge(3600);
	}

	@Bean
	public UrlHandlerFilter trailingSlashFilter() {
		return UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build();
	}

	// Escopo declarativo via URL pattern: o servlet container so invoca este
	// filtro pra /actuator/*, em vez do filtro checar o prefixo em runtime a
	// cada requisicao da aplicacao (achado de code review).
	@Bean
	public FilterRegistrationBean<ActuatorNoCacheFilter> actuatorNoCacheFilter() {
		FilterRegistrationBean<ActuatorNoCacheFilter> registration = new FilterRegistrationBean<>(
				new ActuatorNoCacheFilter());
		registration.addUrlPatterns("/actuator/*");
		return registration;
	}
}
