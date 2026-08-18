package br.com.rony.spring.boot.estado;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EstadoRequestDTO(
		Long id,
		@NotNull @Size(min = 3, max = 100) String nome,
		@NotNull @Size(min = 2, max = 2) String sigla) {

	public Estado toEntity() {
		Estado entidade = new Estado();
		entidade.setId(id);
		entidade.setNome(nome);
		entidade.setSigla(sigla);
		return entidade;
	}
}
