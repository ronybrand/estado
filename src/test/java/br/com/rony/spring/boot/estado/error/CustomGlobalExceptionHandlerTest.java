package br.com.rony.spring.boot.estado.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import jakarta.validation.ConstraintViolationException;

import br.com.rony.spring.boot.estado.EstadoNaoEncontradoException;

public class CustomGlobalExceptionHandlerTest {

	private final CustomGlobalExceptionHandler handler = new CustomGlobalExceptionHandler();

	private Logger logger;
	private ListAppender<ILoggingEvent> logs;

	@BeforeEach
	public void capturaLogs() {
		logger = (Logger) LoggerFactory.getLogger(CustomGlobalExceptionHandler.class);
		logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach
	public void limpaLogsEMdc() {
		logger.detachAppender(logs);
		MDC.clear();
	}

	@Test
	public void constraintViolationRetorna400ComMensagemESemLog() {
		ConstraintViolationException ex = new ConstraintViolationException("sigla invalida", null);

		ResponseEntity<ErrorResponseDto> resposta = handler.requisicaoInvalida(ex);

		assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
		assertEquals("sigla invalida", resposta.getBody().message());
		assertTrue(logs.list.isEmpty());
	}

	@Test
	public void methodArgumentTypeMismatchRetorna400SemLog() {
		MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
		when(ex.getMessage()).thenReturn("tipo invalido pro parametro id");

		ResponseEntity<ErrorResponseDto> resposta = handler.requisicaoInvalida(ex);

		assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
		assertEquals("tipo invalido pro parametro id", resposta.getBody().message());
		assertTrue(logs.list.isEmpty());
	}

	@Test
	public void dataIntegrityViolationRetorna409ComWarnENaoVazaMensagemDoDriver() {
		// achado validando end-to-end contra um Postgres real: getMessage() de
		// DataIntegrityViolationException inclui o SQL bruto e o nome da
		// constraint (ex: "could not execute statement [ERROR: duplicate key
		// value violates unique constraint \"uniquenomeconstraint\"...") - nunca
		// deveria ir pro cliente, mesma logica do handler catch-all.
		// Status 409 (nao 400): duplicata e um conflito com o estado atual do
		// recurso, nao um input malformado.
		DataIntegrityViolationException ex = new DataIntegrityViolationException(
				"could not execute statement [ERROR: duplicate key value violates unique constraint \"uniquenomeconstraint\"]");

		ResponseEntity<ErrorResponseDto> resposta = handler.integridadeDeDadosViolada(ex);

		assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
		assertFalse(resposta.getBody().message().contains("uniquenomeconstraint"));
		assertFalse(resposta.getBody().message().toLowerCase().contains("statement"));
		assertEquals(1, logs.list.size());
		assertEquals(Level.WARN, logs.list.get(0).getLevel());
	}

	@Test
	public void excecaoInesperadaRetorna500ComErroENaoVazaMensagemInterna() {
		RuntimeException ex = new RuntimeException("detalhe interno sensivel de implementacao");

		ResponseEntity<ErrorResponseDto> resposta = handler.erroInesperado(ex);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
		assertEquals("Erro interno do servidor", resposta.getBody().message());
		assertEquals(1, logs.list.size());
		assertEquals(Level.ERROR, logs.list.get(0).getLevel());
		assertNotNull(logs.list.get(0).getThrowableProxy());
		assertEquals(RuntimeException.class.getName(), logs.list.get(0).getThrowableProxy().getClassName());
	}

	@Test
	public void corpoDeErroInclueRequestIdDoMdcQuandoPresente() {
		MDC.put("requestId", "abc-123");
		ConstraintViolationException ex = new ConstraintViolationException("invalido", null);

		ResponseEntity<ErrorResponseDto> resposta = handler.requisicaoInvalida(ex);

		assertEquals("abc-123", resposta.getBody().requestId());
	}

	@Test
	public void corpoDeErroTemRequestIdNuloQuandoMdcVazio() {
		ConstraintViolationException ex = new ConstraintViolationException("invalido", null);

		ResponseEntity<ErrorResponseDto> resposta = handler.requisicaoInvalida(ex);

		assertNull(resposta.getBody().requestId());
	}

	@Test
	public void methodArgumentNotValidRetorna400ComMensagemDosCamposESemLog() throws NoSuchMethodException {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "estadoRequestDTO");
		bindingResult.addError(new FieldError("estadoRequestDTO", "sigla", "must not be null"));
		MethodParameter parametro = new MethodParameter(
				CustomGlobalExceptionHandlerTest.class.getDeclaredMethod("metodoFalso", String.class), 0);
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parametro, bindingResult);

		ResponseEntity<ErrorResponseDto> resposta = handler.validacaoFalhou(ex);

		assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
		assertTrue(resposta.getBody().message().contains("sigla"));
		assertTrue(resposta.getBody().message().contains("must not be null"));
		assertTrue(logs.list.isEmpty());
	}

	private void metodoFalso(String arg) {
		// usado somente para obter um MethodParameter valido no teste acima
	}

	@Test
	public void estadoNaoEncontradoRetorna404ComMensagemESemLog() {
		EstadoNaoEncontradoException ex = new EstadoNaoEncontradoException("Estado nao encontrado: id=999");

		ResponseEntity<ErrorResponseDto> resposta = handler.estadoNaoEncontrado(ex);

		assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
		assertEquals("Estado nao encontrado: id=999", resposta.getBody().message());
		assertTrue(logs.list.isEmpty());
	}
}
