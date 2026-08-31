package br.com.rony.spring.boot.estado.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties("jwt")
public class JwtProperty {

    // Segredo HMAC (assinatura simetrica) - o mesmo processo emite e valida o
    // token, entao nao ha ganho em usar par assimetrico (RSA), so mais
    // complexidade de gerenciar chave publica/privada (ADR 0017).
    @NotBlank
    private String secret;

    // Precisa ser > 0: um valor 0 (ou negativo) emitiria token ja expirado no
    // instante de criacao - falha na inicializacao em vez de silenciosamente
    // quebrar login em producao.
    @Positive
    private long expirationMinutes;

}
