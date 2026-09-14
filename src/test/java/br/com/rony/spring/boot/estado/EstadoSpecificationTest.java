package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// EstadoRepositoryIT ja prova o comportamento real contra Postgres (LOWER/LIKE
// aplicados pelo banco), mas so roda com Docker disponivel (Testcontainers) - o
// relatorio de cobertura que o CI reporta pro Codecov (build/reports/jacoco/test)
// e so da suite "test", que nunca invoca de fato o toPredicate() de uma
// Specification (Hibernate so chama isso na execucao real da query). Este teste
// exercita o predicado diretamente com mocks do Criteria API, sem precisar de
// Hibernate/banco, fechando esse gap de cobertura no relatorio de patch.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EstadoSpecificationTest {

    @Mock
    Root<Estado> root;

    @Mock
    CriteriaQuery<?> query;

    @Mock
    CriteriaBuilder builder;

    @Mock
    Path<String> nomePath;

    @Mock
    Path<String> siglaPath;

    @Mock
    Expression<String> nomeLower;

    @Mock
    Expression<String> siglaLower;

    @Mock
    Predicate likeNome;

    @Mock
    Predicate likeSigla;

    @Mock
    Predicate ou;

    @Mock
    Predicate semFiltro;

    @Test
    void semBuscaRetornaConjunctionSemAplicarLikeEmNadaAcima() {
        when(builder.conjunction()).thenReturn(semFiltro);

        Predicate resultado = EstadoSpecification.comBusca("   ").toPredicate(root, query, builder);

        assertSame(semFiltro, resultado);
        verify(builder, never()).like(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(builder, never()).or(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void comBuscaFiltraNomeOuSiglaComTermoEmMinusculoEEntreWildcards() {
        when(root.<String>get("nome")).thenReturn(nomePath);
        when(root.<String>get("sigla")).thenReturn(siglaPath);
        when(builder.lower(nomePath)).thenReturn(nomeLower);
        when(builder.lower(siglaPath)).thenReturn(siglaLower);
        when(builder.like(nomeLower, "%santa%")).thenReturn(likeNome);
        when(builder.like(siglaLower, "%santa%")).thenReturn(likeSigla);
        when(builder.or(likeNome, likeSigla)).thenReturn(ou);

        Predicate resultado = EstadoSpecification.comBusca(" Santa ").toPredicate(root, query, builder);

        assertSame(ou, resultado);
        verify(builder).like(nomeLower, "%santa%");
        verify(builder).like(siglaLower, "%santa%");
        verify(builder).or(likeNome, likeSigla);
    }
}
