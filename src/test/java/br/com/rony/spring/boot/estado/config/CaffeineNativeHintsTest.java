package br.com.rony.spring.boot.estado.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class CaffeineNativeHintsTest {

    private static final String PACOTE = "com.github.benmanes.caffeine.cache.";

    private final RuntimeHints hints = new RuntimeHints();

    CaffeineNativeHintsTest() {
        new CaffeineNativeHints().registerHints(hints, getClass().getClassLoader());
    }

    @Test
    void registraAClasseGeradaUsadaPeloRateLimitFilter() {
        assertThat(RuntimeHintsPredicates.reflection().onType(TypeReference.of(PACOTE + "SSA")))
                .accepts(hints);
    }

    @Test
    void naoRegistraClassesComunsDoCaffeine() {
        assertThat(RuntimeHintsPredicates.reflection().onType(TypeReference.of(PACOTE + "Caffeine")))
                .rejects(hints);
    }

    @Test
    void propagaAFalhaAoVarrerAsClasses() {
        ClassLoader quebrado = new ClassLoader() {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                throw new IOException("sem acesso");
            }
        };

        var registrador = new CaffeineNativeHints();
        var hintsVazios = new RuntimeHints();

        assertThatThrownBy(() -> registrador.registerHints(hintsVazios, quebrado))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);
    }
}
