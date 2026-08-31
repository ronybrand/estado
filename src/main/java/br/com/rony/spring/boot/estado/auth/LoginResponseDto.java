package br.com.rony.spring.boot.estado.auth;

public record LoginResponseDto(String token, long expiresInSeconds) {
}
