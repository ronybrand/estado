package br.com.rony.spring.boot.estado.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("admin")
public class AdminProperty {

    private String username;

    // Hash BCrypt, nunca a senha em texto plano (ADR 0017).
    private String passwordHash;

}
