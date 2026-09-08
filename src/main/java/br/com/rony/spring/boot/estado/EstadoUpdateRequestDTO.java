package br.com.rony.spring.boot.estado;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// id nao faz parte do corpo: PUT /estado/{id} identifica o recurso pela URL
// (convencao REST), nao pelo payload - evita o contrato ambiguo de antes,
// onde o id vinha do corpo em vez do path. Ver EstadoController.atualizar.
public record EstadoUpdateRequestDTO(
        @NotNull @Size(min = Estado.NOME_MIN_LENGTH, max = Estado.NOME_MAX_LENGTH) String nome,
        @NotNull @Size(min = Estado.SIGLA_LENGTH, max = Estado.SIGLA_LENGTH) String sigla) {

    public Estado toEntity(Long id) {
        Estado entidade = new Estado();
        entidade.setId(id);
        entidade.setNome(nome);
        entidade.setSigla(sigla);
        return entidade;
    }
}
