package br.com.rony.spring.boot.estado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Sobe um Postgres real via Testcontainers - as unique constraints de nome/sigla
// (Estado.java) sao aplicadas pelo banco, nao pela app, entao so um teste contra
// Postgres de verdade prova o fluxo fim a fim que CustomGlobalExceptionHandler
// espera (DataIntegrityViolationException -> 409).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class EstadoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    EstadoRepository repository;

    private Estado novoEstado(String nome, String sigla) {
        Estado estado = new Estado();
        estado.setNome(nome);
        estado.setSigla(sigla);
        estado.setDataHoraCadastro(LocalDateTime.now());
        return estado;
    }

    @Test
    void salvaEBuscaEstadoPersistidoNoPostgresReal() {
        Estado salvo = repository.save(novoEstado("Santa Catarina", "sc"));

        Estado encontrado = repository.findById(salvo.getId()).orElseThrow();

        assertThat(encontrado.getNome()).isEqualTo("Santa Catarina");
        assertThat(encontrado.getSigla()).isEqualTo("SC");
    }

    @Test
    void siglaDuplicadaViolaConstraintUniqueDoPostgres() {
        repository.saveAndFlush(novoEstado("Santa Catarina", "SC"));

        assertThatThrownBy(() -> repository.saveAndFlush(novoEstado("Rio Grande do Sul", "SC")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nomeDuplicadoViolaConstraintUniqueDoPostgres() {
        repository.saveAndFlush(novoEstado("Santa Catarina", "SC"));

        assertThatThrownBy(() -> repository.saveAndFlush(novoEstado("Santa Catarina", "RS")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
