package br.com.rony.spring.boot.estado.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties("pagination")
public class PaginationProperty {

    // Precisa ser > 0: o pageableCustomizer (WebConfig) usa este valor direto
    // como maxPageSize do resolver do Spring Data - um valor 0 ou negativo
    // clamparia toda pagina pra um tamanho invalido silenciosamente, em vez de
    // falhar na inicializacao (mesmo padrao de JwtProperty, ver ADR 0017).
    @Positive
    private int maxSize = 100;

}
