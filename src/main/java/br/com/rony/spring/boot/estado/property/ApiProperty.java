package br.com.rony.spring.boot.estado.property;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("api")
public class ApiProperty {

    // Nome no singular mantido de proposito: a chave YAML (origin-permitida)
    // e a env var (API_ORIGIN_PERMITIDA) de producao ja existem com esse nome
    // - o binding relaxado do Spring aceita uma lista aqui sem precisar
    // renomear nenhuma das duas (so o valor vira uma string separada por
    // virgula quando houver mais de uma origem).
    private List<String> originPermitida = List.of("http://localhost:8000");

}
