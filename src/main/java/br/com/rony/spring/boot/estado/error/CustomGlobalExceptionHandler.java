package br.com.rony.spring.boot.estado.error;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.rony.spring.boot.estado.EstadoNaoEncontradoException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class CustomGlobalExceptionHandler {

    private static final String MENSAGEM_ERRO_INTERNO = "Erro interno do servidor";
    private static final String MENSAGEM_INTEGRIDADE_DADOS = "Dado duplicado ou restricao de integridade violada";

    // Erro esperado de input do cliente - tráfego normal, logar em WARN aqui
    // vira só ruído em produção real.
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDto> requisicaoInvalida(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(ex.getMessage()));
    }

    // Falha de @Valid @RequestBody (bean validation em EstadoRequestDTO) -
    // sem este handler dedicado, cai no ProblemDetail padrao do Spring, com
    // formato diferente de todo o resto da API.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> validacaoFalhou(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatarErroDeCampo)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(mensagem));
    }

    private String formatarErroDeCampo(FieldError erro) {
        return erro.getField() + ": " + erro.getDefaultMessage();
    }

    // Mensagem autorada pelo codigo da app (ex: "Sigla ja cadastrada") -
    // diferente de excecoes de infraestrutura, e seguro devolver ao cliente.
    @ExceptionHandler(ExcecaoRegraNegocio.class)
    public ResponseEntity<ErrorResponseDto> regraDeNegocioViolada(ExcecaoRegraNegocio ex) {
        log.warn("Requisicao invalida ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(ex.getMessage()));
    }

    // getMessage() aqui vem do driver JDBC/Hibernate e inclui SQL bruto e nome
    // de constraint (achado validando contra um Postgres real) - nunca vai pro
    // cliente, so pro log. Mesmo cuidado do handler catch-all de Exception.
    // Status 409 (nao 400): duplicata e um conflito com o estado atual do
    // recurso, nao um input malformado.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> integridadeDeDadosViolada(DataIntegrityViolationException ex) {
        log.warn("Requisicao invalida ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo(MENSAGEM_INTEGRIDADE_DADOS));
    }

    // Busca por id que nao existe - recurso nao encontrado, tratamento
    // separado de ExcecaoRegraNegocio (400) pois semanticamente e 404.
    @ExceptionHandler(EstadoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponseDto> estadoNaoEncontrado(EstadoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(ex.getMessage()));
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
