package br.com.rony.spring.boot.estado.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

	@Bean
	public UrlHandlerFilter trailingSlashFilter() {
		return UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build();
	}
}
