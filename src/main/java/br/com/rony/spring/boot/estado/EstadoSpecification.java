package br.com.rony.spring.boot.estado;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

// Specification em vez de metodo derivado (findByNomeContainingOrSiglaContaining) porque o
// predicado e condicional: "busca" pode vir vazio (sem filtro nenhum) ou preenchido (filtra
// nome OU sigla, nao AND) - um metodo derivado fixo nao expressa isso sem duplicar a query.
public final class EstadoSpecification {

    private EstadoSpecification() {
    }

    // Um unico campo de busca, nao nome/sigla separados: o caso de uso real e um usuario
    // digitando um termo sem saber se e nome ou sigla (ex: "SC" ou "Santa Catarina") - dois
    // campos distintos exigiriam ele escolher, e uma combinacao AND dos dois nunca bateria
    // (nenhum estado tem nome igual a sigla).
    private static final char ESCAPE_CHAR = '\\';

    public static Specification<Estado> comBusca(String busca) {
        if (StringUtils.isBlank(busca)) {
            return (root, query, builder) -> builder.conjunction();
        }
        // Escapa "%" e "_" do termo digitado para que sejam tratados como texto literal, nao
        // como coringas do LIKE (ex: buscar "a_b" nao deve casar "axb").
        String escapado = busca.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String termo = "%" + escapado + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("nome")), termo, ESCAPE_CHAR),
                builder.like(builder.lower(root.get("sigla")), termo, ESCAPE_CHAR));
    }
}
