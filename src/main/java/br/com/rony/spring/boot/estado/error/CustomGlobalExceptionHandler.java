package br.com.rony.spring.boot.estado.error;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.com.rony.spring.boot.estado.EstadoNaoEncontradoException;
import br.com.rony.spring.boot.estado.auth.InvalidCredentialsException;
import br.com.rony.spring.boot.estado.config.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class CustomGlobalExceptionHandler {

    private static final String MENSAGEM_ERRO_INTERNO = "Erro interno do servidor";
    private static final String MENSAGEM_INTEGRIDADE_DADOS = "Dado duplicado ou restricao de integridade violada";
    private static final String MENSAGEM_SORT_INVALIDO = "Parametro de ordenacao invalido";
    private static final String MENSAGEM_REQUISICAO_INVALIDA = "Parametro de requisicao invalido";

    // Erro esperado de input do cliente - trafego normal, logar em WARN aqui
    // vira so ruido em producao real. Mensagem generica (nao ex.getMessage())
    // pelo mesmo motivo do handler de sort: essas excecoes citam nome de
    // parametro/metodo do controller, detalhe interno que nao devia vazar.
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDto> requisicaoInvalida(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(MENSAGEM_REQUISICAO_INVALIDA));
    }

    // A mensagem do Spring Data inclui o nome da propriedade procurada e o
    // tipo da entidade. Para sort invalido, isso revelaria detalhes do modelo.
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponseDto> sortInvalido(PropertyReferenceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(MENSAGEM_SORT_INVALIDO));
    }

    // Falha de @Valid @RequestBody (bean validation nos DTOs de request) -
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

    // Busca por id que nao existe - recurso nao encontrado, semanticamente e 404.
    @ExceptionHandler(EstadoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponseDto> estadoNaoEncontrado(EstadoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(ex.getMessage()));
    }

    // Login com usuario/senha invalidos (ADR 0017) - nao confundir com o 401
    // de token ausente/invalido em rota protegida, tratado pelo
    // AuthenticationEntryPoint do SecurityConfig (roda fora deste handler).
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> credenciaisInvalidas(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(corpo(ex.getMessage()));
    }

    // Sem rota/recurso estatico correspondente a URL pedida (ex: typo no path,
    // ou Swagger UI desligado via SPRINGDOC_SWAGGER_UI_ENABLED=false) - sem
    // este handler, cai no catch-all de Exception e vira 500 em vez de 404
    // pra qualquer URL invalida (achado testando o Swagger desligado).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> rotaNaoEncontrada(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo("Recurso nao encontrado"));
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
        return new ErrorResponseDto(mensagem, MDC.get(RequestIdFilter.MDC_KEY));
    }
}
