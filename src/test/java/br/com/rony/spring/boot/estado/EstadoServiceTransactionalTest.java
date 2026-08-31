package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

// Documenta a expectativa de otimizacao: metodos somente-leitura devem
// declarar readOnly=true (dirty checking e flush desligados pelo Hibernate),
// separado dos metodos de escrita que herdam o @Transactional da classe.
class EstadoServiceTransactionalTest {

	@Test
	void listarDeveSerSomenteLeitura() throws NoSuchMethodException {
		assertReadOnly("listar");
	}

	@Test
	void getDomainByIdDeveSerSomenteLeitura() throws NoSuchMethodException {
		assertReadOnly("getDomainById", long.class);
	}

	@Test
	void salvarNaoDeveSerSomenteLeitura() throws NoSuchMethodException {
		assertNotReadOnly("salvar", Estado.class);
	}

	@Test
	void atualizarNaoDeveSerSomenteLeitura() throws NoSuchMethodException {
		assertNotReadOnly("atualizar", Estado.class);
	}

	@Test
	void excluirNaoDeveSerSomenteLeitura() throws NoSuchMethodException {
		assertNotReadOnly("excluir", long.class);
	}

	private void assertReadOnly(String nomeMetodo, Class<?>... parametros) throws NoSuchMethodException {
		assertTrue(readOnly(nomeMetodo, parametros), nomeMetodo + " deveria ser @Transactional(readOnly = true)");
	}

	private void assertNotReadOnly(String nomeMetodo, Class<?>... parametros) throws NoSuchMethodException {
		assertFalse(readOnly(nomeMetodo, parametros), nomeMetodo + " nao deveria ser readOnly");
	}

	private static final AnnotationTransactionAttributeSource SOURCE = new AnnotationTransactionAttributeSource();

	private boolean readOnly(String nomeMetodo, Class<?>... parametros) throws NoSuchMethodException {
		Method metodo = EstadoService.class.getMethod(nomeMetodo, parametros);
		TransactionAttribute atributo = SOURCE.getTransactionAttribute(metodo, EstadoService.class);
		return atributo.isReadOnly();
	}
}
