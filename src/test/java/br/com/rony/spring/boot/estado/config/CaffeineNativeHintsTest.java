package br.com.rony.spring.boot.estado.config;

import static org.assertj.core.api.Assertions.assertThat;

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
}
