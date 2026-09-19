package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EstadoTest {

    private Locale localePadraoOriginal;

    @BeforeEach
    void guardaLocalePadrao() {
        localePadraoOriginal = Locale.getDefault();
    }

    @AfterEach
    void restauraLocalePadrao() {
        Locale.setDefault(localePadraoOriginal);
    }

    @Test
    void setSiglaComValorConvertePraMaiuscula() {
        Estado estado = new Estado();
        estado.setSigla("sc");
        assertEquals("SC", estado.getSigla());
    }

    @Test
    void setSiglaConverteParaMaiusculaIndependenteDoLocalePadrao() {
        // achado de code review: toUpperCase() sem Locale usa o locale padrao
        // da JVM - em turco/azeri, "pi".toUpperCase() vira "Pİ" (I pontuado)
        // em vez de "PI", quebrando a sigla de um estado como Piaui.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        Estado estado = new Estado();
        estado.setSigla("pi");

        assertEquals("PI", estado.getSigla());
    }

    @Test
    void setSiglaComNuloNaoLancaExcecao() {
        // Jackson chama o setter durante o deserialize do JSON antes do @Valid
        // rodar - se o corpo da requisicao nao trouxer "sigla", o setter e
        // chamado com null e nao pode estourar NPE (isso viraria 500 em vez
        // do 400 esperado da validacao @NotNull).
        Estado estado = new Estado();
        assertDoesNotThrow(() -> estado.setSigla(null));
        assertNull(estado.getSigla());
    }
}
