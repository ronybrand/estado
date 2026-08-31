package br.com.rony.spring.boot.estado.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("rate-limit")
public class RateLimitProperty {

    // Limite generoso o suficiente pra nao incomodar uso normal (inclusive
    // testes manuais/Postman em rajada), mas baixo o bastante pra travar um
    // laco de retry descontrolado ou scraping simples - ver ADR 0016.
    private int capacidade = 60;

    private int janelaSegundos = 60;

}
