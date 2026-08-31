package br.com.rony.spring.boot.estado.config;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.rony.spring.boot.estado.error.ErrorResponseDto;
import br.com.rony.spring.boot.estado.property.RateLimitProperty;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;

// Rate limiting por IP, em memoria (sem Redis - uma unica instancia EC2, ver
// ADR 0016). Depende de server.forward-headers-strategy: framework
// (application.yml) para que getRemoteAddr() resolva o IP real do cliente a
// partir do X-Forwarded-For que o Caddy injeta - sem isso, todo trafego
// pareceria vir do proprio Caddy (mesmo IP interno) e o limite acabaria
// sendo global, nao por cliente. Seguro confiar nesse header aqui porque o
// container so e alcancavel via Caddy (porta 8080 nunca publicada pro host,
// ver ADR 0012) - ninguem consegue falar direto com a app pra forjar o
// header.
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

	private final RateLimitProperty rateLimitProperty;
	// Instancia propria, nao o bean do Spring: em alguns slices de teste
	// (@WebMvcTest) o ObjectMapper do Jackson ainda nao esta disponivel no
	// momento em que este filtro e criado (achado rodando a suite completa) -
	// mesma escolha de auto-suficiencia do RequestIdFilter, sem dependencia
	// externa alem do necessario.
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	public RateLimitFilter(RateLimitProperty rateLimitProperty) {
		this.rateLimitProperty = rateLimitProperty;
	}

	@Override
	public int getOrder() {
		// Logo depois do RequestIdFilter (HIGHEST_PRECEDENCE): precisa do
		// requestId ja no MDC pra ecoar no corpo de erro 429, mas deve rodar
		// antes de qualquer outro processamento pra rejeitar cedo.
		return Ordered.HIGHEST_PRECEDENCE + 1;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String ip = request.getRemoteAddr();
		Bucket bucket = buckets.computeIfAbsent(ip, chave -> novoBucket());

		if (bucket.tryConsume(1)) {
			filterChain.doFilter(request, response);
			return;
		}

		log.warn("Rate limit excedido para IP {}", ip);
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ErrorResponseDto corpo = new ErrorResponseDto(
				"Muitas requisicoes - tente novamente em instantes", MDC.get(RequestIdFilter.MDC_KEY));
		response.getWriter().write(objectMapper.writeValueAsString(corpo));
	}

	private Bucket novoBucket() {
		Bandwidth limite = Bandwidth.classic(rateLimitProperty.getCapacidade(),
				Refill.greedy(rateLimitProperty.getCapacidade(), Duration.ofSeconds(rateLimitProperty.getJanelaSegundos())));
		return Bucket.builder().addLimit(limite).build();
	}
}
