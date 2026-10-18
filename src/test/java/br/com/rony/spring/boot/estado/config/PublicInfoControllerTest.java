package br.com.rony.spring.boot.estado.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class PublicInfoControllerTest {

    private final InfoEndpoint infoEndpoint = mock(InfoEndpoint.class);
    private final PublicInfoController controller = new PublicInfoController(infoEndpoint);

    @Test
    void devolveOMesmoConteudoDoInfoEndpoint() {
        Map<String, Object> info = Map.of("build", Map.of("version", "1.2.3"));
        when(infoEndpoint.info()).thenReturn(info);

        assertEquals(info, controller.info());
    }

    @Test
    void soExisteQuandoOActuatorTemPortaSeparada() {
        ConditionalOnProperty condition = PublicInfoController.class.getAnnotation(ConditionalOnProperty.class);

        assertEquals("management.server.port", condition.value()[0]);
    }
}
