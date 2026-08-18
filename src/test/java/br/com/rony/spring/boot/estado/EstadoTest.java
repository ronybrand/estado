package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class EstadoTest {

	@Test
	public void setSiglaComValorConvertePraMaiuscula() {
		Estado estado = new Estado();
		estado.setSigla("sc");
		assertEquals("SC", estado.getSigla());
	}

	@Test
	public void setSiglaComNuloNaoLancaExcecao() {
		// Jackson chama o setter durante o deserialize do JSON antes do @Valid
		// rodar - se o corpo da requisicao nao trouxer "sigla", o setter e
		// chamado com null e nao pode estourar NPE (isso viraria 500 em vez
		// do 400 esperado da validacao @NotNull).
		Estado estado = new Estado();
		assertDoesNotThrow(() -> estado.setSigla(null));
		assertNull(estado.getSigla());
	}
}
