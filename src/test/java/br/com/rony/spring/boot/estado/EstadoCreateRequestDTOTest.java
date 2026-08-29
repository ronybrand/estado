package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EstadoCreateRequestDTOTest {

	private ValidatorFactory factory;
	private Validator validator;

	@BeforeEach
	public void criaValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterEach
	public void fechaValidator() {
		factory.close();
	}

	private EstadoCreateRequestDTO getDto(String nome, String sigla) {
		return new EstadoCreateRequestDTO(nome, sigla);
	}

	@Test
	public void toEntityMapeiaNomeESiglaComIdNulo() {
		EstadoCreateRequestDTO dto = this.getDto("Santa Catarina", "SC");

		Estado entidade = dto.toEntity();

		assertNull(entidade.getId());
		assertEquals("Santa Catarina", entidade.getNome());
		assertEquals("SC", entidade.getSigla());
	}

	@Test
	public void dtoValidoNaoTemViolacoes() {
		EstadoCreateRequestDTO dto = this.getDto("Santa Catarina", "SC");

		Set<ConstraintViolation<EstadoCreateRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.isEmpty());
	}

	@Test
	public void nomeNuloGeraViolacao() {
		EstadoCreateRequestDTO dto = this.getDto(null, "SC");

		Set<ConstraintViolation<EstadoCreateRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("nome")));
	}

	@Test
	public void siglaComTamanhoDiferenteDeDoisGeraViolacao() {
		EstadoCreateRequestDTO dto = this.getDto("Santa Catarina", "S");

		Set<ConstraintViolation<EstadoCreateRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sigla")));
	}
}
