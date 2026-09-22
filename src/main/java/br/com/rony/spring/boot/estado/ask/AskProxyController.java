package br.com.rony.spring.boot.estado.ask;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// Publico de proposito (mesmo GET /estado/**): o ask-agent e um chat sobre
// dado publico, sem motivo pra exigir login (ver ADR do BFF). A rota existe
// so pra este backend guardar a ASK_API_KEY server-side, nunca no Angular.
@RestController
@RequiredArgsConstructor
@Tag(name = "Ask", description = "Proxy para o estado-ai-agent - guarda a ASK_API_KEY server-side")
public class AskProxyController {

    private final AskProxyService askProxyService;

    @Operation(summary = "Encaminha uma pergunta ao estado-ai-agent")
    @ApiResponse(responseCode = "200", description = "Resposta do modelo de IA")
    @ApiResponse(responseCode = "400", description = "Pergunta ausente ou excede o tamanho maximo")
    @ApiResponse(responseCode = "502", description = "Falha ao consultar o estado-ai-agent")
    @PostMapping("/ask")
    public AskProxyResponseDto ask(@Valid @RequestBody AskProxyRequestDto request, HttpServletRequest httpRequest) {
        return askProxyService.ask(request, httpRequest.getRemoteAddr());
    }
}
