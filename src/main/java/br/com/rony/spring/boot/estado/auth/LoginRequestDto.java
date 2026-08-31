package br.com.rony.spring.boot.estado.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(@NotBlank String username, @NotBlank String password) {
}
