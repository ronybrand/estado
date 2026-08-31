package br.com.rony.spring.boot.estado.auth;

import java.time.Duration;

import jakarta.validation.Valid;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

// Endpoint sempre publico (permitAll no SecurityConfig) - e a porta de
// entrada pra obter o token, nao pode exigir token pra ser chamado.
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AdminProperty adminProperty;
    private final JwtProperty jwtProperty;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        boolean credenciaisValidas = request.username().equals(adminProperty.getUsername())
                && passwordEncoder.matches(request.password(), adminProperty.getPasswordHash());

        if (!credenciaisValidas) {
            throw new InvalidCredentialsException("Usuario ou senha invalidos");
        }

        String token = jwtService.issueToken(request.username());
        long expiresInSeconds = Duration.ofMinutes(jwtProperty.getExpirationMinutes()).getSeconds();
        return new LoginResponseDto(token, expiresInSeconds);
    }
}
