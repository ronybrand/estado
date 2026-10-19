package br.com.rony.spring.boot.estado.ask;

public record AskProxyResponseDto(String answer, Lang language) {

    public AskProxyResponseDto(String answer) {
        this(answer, null);
    }
}
