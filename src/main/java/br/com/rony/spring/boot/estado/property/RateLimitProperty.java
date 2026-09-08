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

    // Login e' alvo natural de tentativa de credenciais. Este limite separado
    // nao divide o bucket generico da API e pode ser mais restritivo sem
    // afetar o uso normal dos demais endpoints.
    private int loginCapacidade = 5;

    private int loginJanelaSegundos = 60;

}
