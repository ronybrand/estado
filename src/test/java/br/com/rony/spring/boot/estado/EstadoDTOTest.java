package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

public class EstadoDTOTest {

	@Test
	public void fromMapeiaTodosOsCamposDaEntidade() {
		Estado entidade = new Estado();
		entidade.setId(1L);
		entidade.setNome("Santa Catarina");
		entidade.setSigla("SC");
		LocalDateTime cadastro = LocalDateTime.now();
		LocalDateTime atualizacao = LocalDateTime.now();
		entidade.setDataHoraCadastro(cadastro);
		entidade.setDataHoraUltimaAtualizacao(atualizacao);

		EstadoDTO dto = EstadoDTO.from(entidade);

		assertEquals(1L, dto.id());
		assertEquals("Santa Catarina", dto.nome());
		assertEquals("SC", dto.sigla());
		assertEquals(cadastro, dto.dataHoraCadastro());
		assertEquals(atualizacao, dto.dataHoraUltimaAtualizacao());
	}

	@Test
	public void fromComDataHoraUltimaAtualizacaoNulaMantemNulo() {
		Estado entidade = new Estado();
		entidade.setId(1L);
		entidade.setNome("Santa Catarina");
		entidade.setSigla("SC");
		entidade.setDataHoraCadastro(LocalDateTime.now());

		EstadoDTO dto = EstadoDTO.from(entidade);

		assertNull(dto.dataHoraUltimaAtualizacao());
	}
}
