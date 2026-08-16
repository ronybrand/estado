package br.com.rony.spring.boot.estado.error;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class CustomGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomGlobalExceptionHandler.class);
    private static final String MENSAGEM_ERRO_INTERNO = "Erro interno do servidor";

    // Erro esperado de input do cliente - tráfego normal, logar em WARN aqui
    // vira só ruído em produção real.
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDto> requisicaoInvalida(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(ex.getMessage()));
    }

    // Regra de negócio violada ou integridade de dado - vale o operador saber.
    @ExceptionHandler({ExcecaoRegraNegocio.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponseDto> requisicaoInvalidaComAlerta(Exception ex) {
        log.warn("Requisicao invalida ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(ex.getMessage()));
    }

    // Catch-all: nada previsto chegou até aqui. Loga stack trace completo pra
    // diagnóstico, mas NUNCA devolve ex.getMessage() pro cliente - podia
    // vazar detalhe interno de implementação (ex: mensagem de exceção do
    // driver JDBC).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> erroInesperado(Exception ex) {
        log.error("Erro inesperado processando a requisicao", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo(MENSAGEM_ERRO_INTERNO));
    }

    private ErrorResponseDto corpo(String mensagem) {
        return new ErrorResponseDto(mensagem, MDC.get("requestId"));
    }
}
