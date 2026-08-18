package br.com.rony.spring.boot.estado;

import java.util.Date;

public record EstadoDTO(Long id, String nome, String sigla, Date dataHoraCadastro, Date dataHoraUltimaAtualizacao) {

	public static EstadoDTO from(Estado entidade) {
		return new EstadoDTO(entidade.getId(), entidade.getNome(), entidade.getSigla(),
				entidade.getDataHoraCadastro(), entidade.getDataHoraUltimaAtualizacao());
	}
}
