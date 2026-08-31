package br.com.rony.spring.boot.estado.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

// So popula o contexto de autenticacao quando o token e valido - nao decide
// quem pode acessar o que (isso e regra do SecurityConfig#authorizeHttpRequests).
// Token ausente/invalido segue a cadeia sem autenticacao; se a rota exigir
// autenticacao, o AuthenticationEntryPoint cuida do 401 mais a frente (ADR
// 0017) - mesma separacao "autenticacao vs autorizacao" idiomatica do Spring
// Security.
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIXO)) {
            String token = header.substring(PREFIXO.length());
            try {
                String username = jwtService.validateAndGetSubject(token);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(username, null, List.of()));
            } catch (JwtException | IllegalArgumentException ex) {
                // Token invalido/expirado/adulterado - nao autentica, segue sem
                // contexto (ver comentario da classe).
            }
        }
        filterChain.doFilter(request, response);
    }
}
