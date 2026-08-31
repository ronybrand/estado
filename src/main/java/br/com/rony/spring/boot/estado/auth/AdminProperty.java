package br.com.rony.spring.boot.estado.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties("admin")
public class AdminProperty {

    @NotBlank
    private String username;

    // Hash BCrypt, nunca a senha em texto plano (ADR 0017).
    @NotBlank
    private String passwordHash;

}
