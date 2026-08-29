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
	public void criaValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterEach
	public void fechaValidator() {
		factory.close();
	}

	private EstadoUpdateRequestDTO getDto(Long id, String nome, String sigla) {
		return new EstadoUpdateRequestDTO(id, nome, sigla);
	}

	@Test
	public void toEntityMapeiaIdNomeESigla() {
		EstadoUpdateRequestDTO dto = this.getDto(1L, "Santa Catarina", "SC");

		Estado entidade = dto.toEntity();

		assertEquals(1L, entidade.getId());
		assertEquals("Santa Catarina", entidade.getNome());
		assertEquals("SC", entidade.getSigla());
	}

	@Test
	public void dtoValidoNaoTemViolacoes() {
		EstadoUpdateRequestDTO dto = this.getDto(1L, "Santa Catarina", "SC");

		Set<ConstraintViolation<EstadoUpdateRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.isEmpty());
	}

	@Test
	public void idNuloGeraViolacao() {
		EstadoUpdateRequestDTO dto = this.getDto(null, "Santa Catarina", "SC");

		Set<ConstraintViolation<EstadoUpdateRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("id")));
	}

	@Test
	public void nomeNuloGeraViolacao() {
		EstadoUpdateRequestDTO dto = this.getDto(1L, null, "SC");

		Set<ConstraintViolation<EstadoUpdateRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("nome")));
	}

	@Test
	public void siglaComTamanhoDiferenteDeDoisGeraViolacao() {
		EstadoUpdateRequestDTO dto = this.getDto(1L, "Santa Catarina", "S");

		Set<ConstraintViolation<EstadoUpdateRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sigla")));
	}
}
