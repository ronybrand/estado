package br.com.rony.spring.boot.estado;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// id obrigatorio (achado de code review - EstadoRequestDTO nao validava id,
// e EstadoService.atualizar desembala domain.getId() (Long) num long, entao
// um PUT sem id passava da validacao e quebrava com NullPointerException,
// virando 500 em vez de um 400 tratado por MethodArgumentNotValidException).
public record EstadoUpdateRequestDTO(
		@NotNull Long id,
		@NotNull @Size(min = Estado.NOME_MIN_LENGTH, max = Estado.NOME_MAX_LENGTH) String nome,
		@NotNull @Size(min = Estado.SIGLA_LENGTH, max = Estado.SIGLA_LENGTH) String sigla) {

	public Estado toEntity() {
		Estado entidade = new Estado();
		entidade.setId(id);
		entidade.setNome(nome);
		entidade.setSigla(sigla);
		return entidade;
	}
}
