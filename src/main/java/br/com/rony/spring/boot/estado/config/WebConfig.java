package br.com.rony.spring.boot.estado.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

import br.com.rony.spring.boot.estado.property.ApiProperty;

@Configuration
@EnableConfigurationProperties(ApiProperty.class)
public class WebConfig implements WebMvcConfigurer {

	private final ApiProperty apiProperty;

	public WebConfig(ApiProperty apiProperty) {
		this.apiProperty = apiProperty;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/estado/**")
				.allowedOrigins(apiProperty.getOriginPermitida())
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("Authorization", "Content-Type", "Accept")
				.allowCredentials(true)
				.maxAge(3600);
	}

	// Spring Framework 7 parou de tratar "/x" e "/x/" como equivalentes por
	// padrao; isso restaura o comportamento antigo pra todos os mappings,
	// sem precisar declarar {"", "/"} em cada endpoint.
	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		PathPatternParser parser = new PathPatternParser();
		parser.setMatchOptionalTrailingSeparator(true);
		configurer.setPatternParser(parser);
	}
}
