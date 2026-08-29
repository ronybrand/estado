package br.com.rony.spring.boot.estado.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("api")
public class ApiProperty {

    private String originPermitida = "http://localhost:8000";

}
