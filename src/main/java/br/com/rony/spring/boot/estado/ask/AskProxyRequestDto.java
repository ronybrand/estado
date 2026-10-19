package br.com.rony.spring.boot.estado.ask;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Mesmos limites do AskRequest do estado-ai-agent: reproduzi-los aqui evita
// repassar uma pergunta que o upstream so rejeitaria depois, gastando uma
// chamada de rede a toa.
public record AskProxyRequestDto(
        @NotBlank(message = "{error.question.required}")
        @Size(max = 1000, message = "{error.question.max}")
        String question) {
}
