package br.com.rony.spring.boot.estado.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("jwt")
public class JwtProperty {

    // Segredo HMAC (assinatura simetrica) - o mesmo processo emite e valida o
    // token, entao nao ha ganho em usar par assimetrico (RSA), so mais
    // complexidade de gerenciar chave publica/privada (ADR 0017).
    private String secret;

    private long expirationMinutes;

}
