package br.com.rony.spring.boot.estado.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperty jwtProperty;

    public String issueToken(String username) {
        Instant agora = Instant.now();
        SecretKey chave = chaveAssinatura();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(jwtProperty.getExpirationMinutes(), ChronoUnit.MINUTES)))
                .signWith(chave)
                .compact();
    }

    // Lanca JwtException (ou subtipo, ex: ExpiredJwtException) se o token for
    // invalido/expirado/adulterado - propaga pro JwtAuthFilter, que trata
    // qualquer falha da mesma forma (nao autentica, deixa a requisicao
    // seguir sem contexto de seguranca).
    public String validateAndGetSubject(String token) {
        return Jwts.parser()
                .verifyWith(chaveAssinatura())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    private SecretKey chaveAssinatura() {
        return Keys.hmacShaKeyFor(jwtProperty.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
