package br.com.rony.spring.boot.estado.config;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Correlaciona todas as linhas de log de uma mesma requisicao (via MDC) e o
// cliente que a fez (via header ecoado) - sem isso, os logs no Grafana/Loki
// nao dao pra agrupar por requisicao. Ver ADR 0012 / plano de observabilidade.
@Component
public class RequestIdFilter extends OncePerRequestFilter implements Ordered {

	static final String HEADER = "X-Request-Id";
	// public: unica fonte de verdade da chave MDC, tambem lida por
	// CustomGlobalExceptionHandler (pacote error) pra ecoar o requestId no
	// corpo de erro.
	public static final String MDC_KEY = "requestId";

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = resolveRequestId(request.getHeader(HEADER));
		response.setHeader(HEADER, requestId);
		MDC.put(MDC_KEY, requestId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			// MDC e thread-local e o pool de threads do Tomcat reaproveita
			// threads - sem isso, uma requisicao futura na mesma thread
			// herdaria este requestId.
			MDC.remove(MDC_KEY);
		}
	}

	private String resolveRequestId(String recebido) {
		if (recebido != null) {
			try {
				return UUID.fromString(recebido).toString();
			} catch (IllegalArgumentException ex) {
				// header recebido nao e um UUID valido - ignora e gera um novo
			}
		}
		return UUID.randomUUID().toString();
	}
}
