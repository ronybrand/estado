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

public class EstadoRequestDTOTest {

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

	private EstadoRequestDTO getDto(Long id, String nome, String sigla) {
		return new EstadoRequestDTO(id, nome, sigla);
	}

	@Test
	public void toEntityMapeiaIdNomeESigla() {
		EstadoRequestDTO dto = this.getDto(1L, "Santa Catarina", "SC");

		Estado entidade = dto.toEntity();

		assertEquals(1L, entidade.getId());
		assertEquals("Santa Catarina", entidade.getNome());
		assertEquals("SC", entidade.getSigla());
	}

	@Test
	public void toEntityComIdNuloMapeiaIdNulo() {
		EstadoRequestDTO dto = this.getDto(null, "Santa Catarina", "SC");

		Estado entidade = dto.toEntity();

		assertNull(entidade.getId());
	}

	@Test
	public void dtoValidoNaoTemViolacoes() {
		EstadoRequestDTO dto = this.getDto(null, "Santa Catarina", "SC");

		Set<ConstraintViolation<EstadoRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.isEmpty());
	}

	@Test
	public void nomeNuloGeraViolacao() {
		EstadoRequestDTO dto = this.getDto(null, null, "SC");

		Set<ConstraintViolation<EstadoRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("nome")));
	}

	@Test
	public void siglaComTamanhoDiferenteDeDoisGeraViolacao() {
		EstadoRequestDTO dto = this.getDto(null, "Santa Catarina", "S");

		Set<ConstraintViolation<EstadoRequestDTO>> violacoes = validator.validate(dto);

		assertTrue(violacoes.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sigla")));
	}
}
