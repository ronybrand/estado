package br.com.rony.spring.boot.estado.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

public class JwtServiceTest {

    private JwtProperty property(long expirationMinutes) {
        JwtProperty property = new JwtProperty();
        property.setSecret("segredo-de-teste-com-pelo-menos-32-bytes-de-tamanho");
        property.setExpirationMinutes(expirationMinutes);
        return property;
    }

    @Test
    public void issueTokenSeguidoDeValidateAndGetSubjectRetornaOMesmoUsername() {
        JwtService service = new JwtService(property(60));

        String token = service.issueToken("admin");

        assertEquals("admin", service.validateAndGetSubject(token));
    }

    @Test
    public void validateAndGetSubjectLancaExcecaoParaTokenExpirado() {
        JwtService service = new JwtService(property(-1));

        String tokenExpirado = service.issueToken("admin");

        assertThrows(ExpiredJwtException.class, () -> service.validateAndGetSubject(tokenExpirado));
    }

    @Test
    public void validateAndGetSubjectLancaExcecaoParaTokenAdulterado() {
        JwtService service = new JwtService(property(60));
        String token = service.issueToken("admin");
        String tokenAdulterado = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        assertThrows(JwtException.class, () -> service.validateAndGetSubject(tokenAdulterado));
    }
}
