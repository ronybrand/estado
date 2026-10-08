package br.com.rony.spring.boot.estado.config;

import java.io.IOException;
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

    private static final Pattern GERADA = Pattern.compile("^[A-Z]+$");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        try {
            var recursos = new PathMatchingResourcePatternResolver(classLoader)
                    .getResources("classpath*:com/github/benmanes/caffeine/cache/*.class");
            for (var recurso : recursos) {
                String arquivo = recurso.getFilename();
                if (arquivo == null) {
                    continue;
                }
                String nome = arquivo.substring(0, arquivo.length() - ".class".length());
                if (GERADA.matcher(nome).matches()) {
                    hints.reflection().registerType(
                            TypeReference.of("com.github.benmanes.caffeine.cache." + nome),
                            MemberCategory.values());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao varrer as classes geradas do Caffeine", e);
        }
    }
}
