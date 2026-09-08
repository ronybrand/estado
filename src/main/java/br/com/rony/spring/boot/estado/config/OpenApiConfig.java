package br.com.rony.spring.boot.estado.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	// Esquema "bearerAuth" habilita o botao Authorize no Swagger UI - sem ele,
	// quem for testar POST/PUT/DELETE em /estado/** (protegidos por JWT, ver
	// ADR 0017) tem que montar o header Authorization manualmente a cada
	// chamada.
	@Bean
	public OpenAPI estadoOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Estado API")
						.description("CRUD paginado das unidades federativas do Brasil, com autenticacao JWT "
								+ "para mutacao (ver ADR 0017).")
						.version("v1"))
				.components(new Components()
						.addSecuritySchemes("bearerAuth", new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}
}
