package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EstadoUpdateRequestDTOTest {

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void criaValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void fechaValidator() {
        factory.close();
    }

    private EstadoUpdateRequestDTO getDto(String nome, String sigla) {
        return new EstadoUpdateRequestDTO(nome, sigla);
    }

    @Test
    void toEntityMapeiaIdDoParametroComNomeESigla() {
        EstadoUpdateRequestDTO dto = this.getDto("Santa Catarina", "SC");

        Estado entidade = dto.toEntity(1L);

        assertEquals(1L, entidade.getId());
        assertEquals("Santa Catarina", entidade.getNome());
        assertEquals("SC", entidade.getSigla());
    }

    @Test
    void dtoValidoNaoTemViolacoes() {
        EstadoUpdateRequestDTO dto = this.getDto("Santa Catarina", "SC");

        Set<ConstraintViolation<EstadoUpdateRequestDTO>> violacoes = validator.validate(dto);

        assertTrue(violacoes.isEmpty());
    }

    @Test
    void nomeNuloGeraViolacao() {
        EstadoUpdateRequestDTO dto = this.getDto(null, "SC");

        Set<ConstraintViolation<EstadoUpdateRequestDTO>> violacoes = validator.validate(dto);

        assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("nome")));
    }

    @Test
    void siglaComTamanhoDiferenteDeDoisGeraViolacao() {
        EstadoUpdateRequestDTO dto = this.getDto("Santa Catarina", "S");

        Set<ConstraintViolation<EstadoUpdateRequestDTO>> violacoes = validator.validate(dto);

        assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sigla")));
    }
}
