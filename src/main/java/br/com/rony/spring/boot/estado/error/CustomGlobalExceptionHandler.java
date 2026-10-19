package br.com.rony.spring.boot.estado.error;

import java.util.Locale;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
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
import br.com.rony.spring.boot.estado.ask.AskUpstreamException;
import br.com.rony.spring.boot.estado.auth.InvalidCredentialsException;
import br.com.rony.spring.boot.estado.config.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class CustomGlobalExceptionHandler {

    private final MessageSource messageSource;

    public CustomGlobalExceptionHandler() {
        this(defaultMessageSource());
    }

    public CustomGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private static MessageSource defaultMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    // Erro esperado de input do cliente - trafego normal, logar em WARN aqui
    // vira so ruido em producao real. Mensagem generica (nao ex.getMessage())
    // pelo mesmo motivo do handler de sort: essas excecoes citam nome de
    // parametro/metodo do controller, detalhe interno que nao devia vazar.
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDto> requisicaoInvalida(Exception ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(mensagem("error.request.invalid", locale)));
    }

    // A mensagem do Spring Data inclui o nome da propriedade procurada e o
    // tipo da entidade. Para sort invalido, isso revelaria detalhes do modelo.
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponseDto> sortInvalido(PropertyReferenceException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(mensagem("error.sort.invalid", locale)));
    }

    // Falha de @Valid @RequestBody (bean validation nos DTOs de request) -
    // sem este handler dedicado, cai no ProblemDetail padrao do Spring, com
    // formato diferente de todo o resto da API.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> validacaoFalhou(MethodArgumentNotValidException ex, Locale locale) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> formatarErroDeCampo(erro, locale))
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(mensagem));
    }

    private String formatarErroDeCampo(FieldError erro, Locale locale) {
        return erro.getField() + ": " + messageSource.getMessage(erro, locale);
    }

    // getMessage() aqui vem do driver JDBC/Hibernate e inclui SQL bruto e nome
    // de constraint (achado validando contra um Postgres real) - nunca vai pro
    // cliente, so pro log. Mesmo cuidado do handler catch-all de Exception.
    // Status 409 (nao 400): duplicata e um conflito com o estado atual do
    // recurso, nao um input malformado.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> integridadeDeDadosViolada(
            DataIntegrityViolationException ex, Locale locale) {
        log.warn("Requisicao invalida ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo(mensagem("error.integrity", locale)));
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
    public ResponseEntity<ErrorResponseDto> credenciaisInvalidas(InvalidCredentialsException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(corpo(mensagem("error.credentials.invalid", locale)));
    }

    // Falha ao chamar o estado-ai-agent. Status default 502 (Bad Gateway,
    // nao 500: o erro nao e deste backend, e do upstream que ele proxya) para
    // timeout/DNS/5xx do upstream - mas AskProxyService pode setar um status
    // diferente (400/429) quando o upstream respondeu algo que faz sentido
    // repassar ao cliente final em vez de mascarar como falha generica.
    // Mensagem da excecao e segura de expor (definida em AskProxyService a
    // partir de um conjunto fixo de casos, nunca da mensagem crua da causa),
    // diferente do catch-all generico abaixo.
    @ExceptionHandler(AskUpstreamException.class)
    public ResponseEntity<ErrorResponseDto> askUpstreamFalhou(AskUpstreamException ex, Locale locale) {
        log.warn("Falha ao consultar o estado-ai-agent", ex);
        String message = ex.getStatus() == HttpStatus.BAD_GATEWAY
                ? mensagem("error.ask.upstream", locale)
                : ex.getMessage();
        return ResponseEntity.status(ex.getStatus()).body(corpo(message));
    }

    // Sem rota/recurso estatico correspondente a URL pedida (ex: typo no path,
    // ou Swagger UI desligado via SPRINGDOC_SWAGGER_UI_ENABLED=false) - sem
    // este handler, cai no catch-all de Exception e vira 500 em vez de 404
    // pra qualquer URL invalida (achado testando o Swagger desligado).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> rotaNaoEncontrada(NoResourceFoundException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(mensagem("error.not-found", locale)));
    }

    // Catch-all: nada previsto chegou até aqui. Loga stack trace completo pra
    // diagnóstico, mas NUNCA devolve ex.getMessage() pro cliente - podia
    // vazar detalhe interno de implementação (ex: mensagem de exceção do
    // driver JDBC).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> erroInesperado(Exception ex, Locale locale) {
        log.error("Erro inesperado processando a requisicao", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo(mensagem("error.internal", locale)));
    }

    private String mensagem(String code, Locale locale) {
        return messageSource.getMessage(code, null, locale);
    }

    private ErrorResponseDto corpo(String mensagem) {
        return new ErrorResponseDto(mensagem, MDC.get(RequestIdFilter.MDC_KEY));
    }
}
