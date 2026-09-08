package br.com.rony.spring.boot.estado.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("pagination")
public class PaginationProperty {

    private int maxSize = 100;

}
