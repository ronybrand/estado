package br.com.rony.spring.boot.estado.ask;

import org.springframework.http.HttpStatus;

public class AskUpstreamException extends RuntimeException {

    private final HttpStatus status;

    // Overload mantido para nao quebrar callers/testes existentes que so
    // conheciam o caso generico de falha de upstream (502) - status
    // explicito e so necessario quando o estado-ai-agent respondeu com um
    // status que faz sentido repassar ao cliente final (ver AskProxyService).
    public AskUpstreamException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, cause);
    }

    public AskUpstreamException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

}
