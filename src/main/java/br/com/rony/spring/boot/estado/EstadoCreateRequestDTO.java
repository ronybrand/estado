package br.com.rony.spring.boot.estado;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Sem campo id de proposito: um create nunca deve aceitar um id vindo do
// cliente (achado de code review - com EstadoRequestDTO compartilhado entre
// create/update, um id enviado no POST era copiado pra entidade e o
// SimpleJpaRepository.save() do Spring Data JPA roteava pra merge() em vez
// de persist(), sobrescrevendo silenciosamente uma linha existente).
public record EstadoCreateRequestDTO(
		@NotNull @Size(min = Estado.NOME_MIN_LENGTH, max = Estado.NOME_MAX_LENGTH) String nome,
		@NotNull @Size(min = Estado.SIGLA_LENGTH, max = Estado.SIGLA_LENGTH) String sigla) {

	public Estado toEntity() {
		Estado entidade = new Estado();
		entidade.setNome(nome);
		entidade.setSigla(sigla);
		return entidade;
	}
}
