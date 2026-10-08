package br.com.rony.spring.boot.estado.config;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

// O Caffeine gera, em build time, classes de cache e de no com nomes so de
// letras maiusculas (SSA, PSA, SSMSA...) e as carrega por reflexao conforme a
// combinacao de opcoes do builder. O native image nao enxerga isso sozinho.
class CaffeineNativeHints implements RuntimeHintsRegistrar {

    private static final String PACOTE = "com.github.benmanes.caffeine.cache.";

    private static final Pattern CLASSE_GERADA = Pattern.compile("^([A-Z]+)\\.class$");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        try {
            var recursos = new PathMatchingResourcePatternResolver(classLoader)
                    .getResources("classpath*:" + PACOTE.replace('.', '/') + "*.class");
            for (var recurso : recursos) {
                var classe = CLASSE_GERADA.matcher(Objects.toString(recurso.getFilename(), ""));
                if (classe.matches()) {
                    hints.reflection().registerType(
                            TypeReference.of(PACOTE + classe.group(1)), MemberCategory.values());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao varrer as classes geradas do Caffeine", e);
        }
    }
}
