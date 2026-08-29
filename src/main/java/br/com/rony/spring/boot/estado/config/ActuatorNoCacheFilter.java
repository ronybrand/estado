package br.com.rony.spring.boot.estado.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

// /actuator/info nao envia nenhum Cache-Control - sem isso, um navegador
// pode reter a resposta antiga (commit/versao de build) por tempo
// indeterminado apos um deploy, escondendo que o backend ja atualizou.
// Registrado (nao @Component) via WebConfig.actuatorNoCacheFilter(), que
// escopa a URL declarativamente - o filtro em si nao precisa mais checar
// o path.
public class ActuatorNoCacheFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader("Cache-Control", "no-store");
		filterChain.doFilter(request, response);
	}
}
