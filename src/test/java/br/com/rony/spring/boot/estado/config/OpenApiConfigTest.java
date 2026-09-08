package br.com.rony.spring.boot.estado.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void expoeTituloEVersaoDaApi() {
        OpenAPI openApi = config.estadoOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Estado API");
        assertThat(openApi.getInfo().getVersion()).isNotBlank();
    }

    @Test
    void registraEsquemaBearerParaOsEndpointsProtegidosPorJwt() {
        OpenAPI openApi = config.estadoOpenApi();

        SecurityScheme esquema = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(esquema.getScheme()).isEqualTo("bearer");
        assertThat(esquema.getBearerFormat()).isEqualTo("JWT");
    }
}
