package br.com.rony.spring.boot.estado.auth;

public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String mensagem) {
        super(mensagem);
    }
}
