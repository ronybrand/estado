package br.com.rony.spring.boot.estado;

public class EstadoNaoEncontradoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EstadoNaoEncontradoException(String mensagem) {
		super(mensagem);
	}
}
