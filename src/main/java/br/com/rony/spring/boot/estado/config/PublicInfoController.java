package br.com.rony.spring.boot.estado.config;

import java.util.Map;

import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Sem o Caddy (Azure Container Apps), nada filtra /actuator/* por path: o
// actuator inteiro vai pra porta de gerenciamento (profile azure), que o
// ingress nunca expoe. Isso derrubaria tambem o /actuator/info, que e publico
// de proposito (footer do front mostra a versao do backend) - este controller
// o serve de volta na porta principal. So existe quando ha porta separada: sem
// ela o proprio actuator ja atende essa rota e o mapping duplicaria.
@RestController
@ConditionalOnProperty("management.server.port")
public class PublicInfoController {

    private final InfoEndpoint infoEndpoint;

    public PublicInfoController(InfoEndpoint infoEndpoint) {
        this.infoEndpoint = infoEndpoint;
    }

    @GetMapping("/actuator/info")
    public Map<String, Object> info() {
        return infoEndpoint.info();
    }
}
