package br.com.rony.spring.boot.estado.auth;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.rony.spring.boot.estado.error.ErrorResponseDto;
import lombok.RequiredArgsConstructor;

// Autenticacao JWT stateless, usuario admin unico - sem UserDetailsService,
// sem form login, sem sessao (ADR 0017). So POST/PUT/DELETE em /estado/**
// exigem token; GET continua publico (dado nao sensivel, mesmo raciocinio da
// ADR 0016).
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({AdminProperty.class, JwtProperty.class})
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // withDefaults() delega pro WebMvcConfigurer#addCorsMappings ja
        // existente em WebConfig (via HandlerMappingIntrospector) - sem isso,
        // o Security bloquearia o preflight OPTIONS antes do CORS do MVC
        // sequer rodar.
        http.cors(Customizer.withDefaults());

        // CSRF nao se aplica aqui: autenticacao e via header Authorization
        // (Bearer token), nunca cookie/sessao - o browser nao anexa esse
        // header automaticamente entre sites, que e o vetor que CSRF explora.
        // Pratica padrao da propria doc do Spring Security pra APIs stateless
        // (ver ADR 0017). Falso positivo conhecido do CodeQL pra esse caso.
        http.csrf(AbstractHttpConfigurer::disable); // lgtm[java/spring-disabled-csrf-protection]

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/estado/**").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Corpo de erro no mesmo formato ErrorResponseDto do resto da API - sem
    // isso, o 401 usaria o body padrao do Spring Security, inconsistente com
    // o resto dos erros. Roda fora do DispatcherServlet/@RestControllerAdvice,
    // mesma tecnica ja usada no RateLimitFilter.
    private AuthenticationEntryPoint authenticationEntryPoint() {
        ObjectMapper objectMapper = new ObjectMapper();
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponseDto corpo = new ErrorResponseDto("Nao autenticado", null);
            response.getWriter().write(objectMapper.writeValueAsString(corpo));
        };
    }
}
