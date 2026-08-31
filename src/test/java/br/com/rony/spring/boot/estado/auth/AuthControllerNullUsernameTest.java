package br.com.rony.spring.boot.estado.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

// admin.username sem valor (ex: default removido do application.yml) nao
// pode virar 500 por NPE - deve continuar recusando login como qualquer
// credencial invalida (ver AuthController#login).
public class AuthControllerNullUsernameTest {

    @Test
    public void loginComAdminUsernameNuloLancaInvalidCredentialsEmVezDeNpe() {
        AdminProperty adminProperty = new AdminProperty();
        adminProperty.setUsername(null);
        adminProperty.setPasswordHash("hash-de-teste");
        JwtProperty jwtProperty = new JwtProperty();
        jwtProperty.setSecret("segredo-de-teste-com-pelo-menos-32-bytes-de-tamanho");
        jwtProperty.setExpirationMinutes(60);
        PasswordEncoder passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }
        };

        AuthController controller = new AuthController(adminProperty, jwtProperty,
                new JwtService(jwtProperty), passwordEncoder);

        assertThrows(InvalidCredentialsException.class,
                () -> controller.login(new LoginRequestDto("admin", "qualquer")));
    }
}
