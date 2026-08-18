package br.com.rony.spring.boot.estado;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = { "nome", "sigla" })
@Table(name = "estado", uniqueConstraints = {
        @UniqueConstraint(columnNames = "nome", name = "uniqueNomeConstraint"),
        @UniqueConstraint(columnNames = "sigla", name = "uniqueSiglaConstraint")})
public class Estado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @Size(min = 3, max = 100)
    @Column(nullable = false, length = 100, unique = true)
    private String nome;

    @NotNull
    @Size(min = 2, max = 2)
    @Column(nullable = false, length = 2, unique = true)
    private String sigla;

	@Column(name = "ts_data_hora_cadastro", nullable = false)
	private LocalDateTime dataHoraCadastro;

	@Column(name = "ts_data_hora_ultima_atualizacao", nullable = true)
	private LocalDateTime dataHoraUltimaAtualizacao;

	// setter manual: a sigla e sempre normalizada para maiuscula - o Lombok
	// @Setter da classe detecta este metodo ja declarado e nao gera outro.
	public void setSigla(String sigla) {
		this.sigla = sigla == null ? null : sigla.toUpperCase();
	}
}
