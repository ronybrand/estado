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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import jakarta.validation.ConstraintViolationException;

import br.com.rony.spring.boot.estado.EstadoNaoEncontradoException;
import br.com.rony.spring.boot.estado.config.RequestIdFilter;

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
		// Sem isso, o evento tambem propaga pro appender de console do root
		// logger - os asserts continuam passando, mas o ERROR/WARN esperado
		// aparece no output do Maven parecendo uma falha real, nao um teste
		// exercitando o catch-all de proposito.
		logger.setAdditive(false);
	}

	@AfterEach
	public void limpaLogsEMdc() {
		logger.detachAppender(logs);
		logger.setAdditive(true);
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
	public void erroInesperadoNaoPropagaLogParaOAppenderRaiz() {
		// O ListAppender do @BeforeEach captura o evento pra assert, mas por
		// padrao o Logback tambem propaga pro appender de console do root
		// logger - fazendo o ERROR esperado deste teste aparecer no output do
		// Maven/CI como se fosse uma falha real. Um appender de teste anexado
		// ao root prova que essa propagacao foi desligada.
		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> rootLogs = new ListAppender<>();
		rootLogs.start();
		root.addAppender(rootLogs);

		try {
			handler.erroInesperado(new RuntimeException("detalhe interno sensivel de implementacao"));

			assertTrue(rootLogs.list.isEmpty());
		} finally {
			root.detachAppender(rootLogs);
		}
	}

	@Test
	public void corpoDeErroInclueRequestIdDoMdcQuandoPresente() {
		MDC.put(RequestIdFilter.MDC_KEY, "abc-123");
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

	@Test
	public void rotaInexistenteRetorna404SemLog() {
		// achado testando o Swagger UI desligado (SPRINGDOC_SWAGGER_UI_ENABLED=false):
		// sem handler dedicado, NoResourceFoundException caia no catch-all de
		// Exception e virava 500 "Erro interno do servidor" pra qualquer URL sem
		// rota correspondente - nao so Swagger, qualquer path/typo incorreto.
		NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "swagger-ui.html", null);

		ResponseEntity<ErrorResponseDto> resposta = handler.rotaNaoEncontrada(ex);

		assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
		assertTrue(logs.list.isEmpty());
	}
}
