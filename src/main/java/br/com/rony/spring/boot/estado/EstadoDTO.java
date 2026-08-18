package br.com.rony.spring.boot.estado;

import java.time.LocalDateTime;

public record EstadoDTO(Long id, String nome, String sigla, LocalDateTime dataHoraCadastro,
		LocalDateTime dataHoraUltimaAtualizacao) {

	public static EstadoDTO from(Estado entidade) {
		return new EstadoDTO(entidade.getId(), entidade.getNome(), entidade.getSigla(),
				entidade.getDataHoraCadastro(), entidade.getDataHoraUltimaAtualizacao());
	}
}
