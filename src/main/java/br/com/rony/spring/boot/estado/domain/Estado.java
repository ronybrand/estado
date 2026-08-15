package br.com.rony.spring.boot.estado.domain;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


@Entity
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
	private Date dataHoraCadastro;
	
	@Column(name = "ts_data_hora_ultima_atualizacao", nullable = true)
	private Date dataHoraUltimaAtualizacao;

	public Estado() {
	}
	
    public Estado(Long id) {
    	super();
		this.id = id;
	}
    
	public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSigla() {
		return sigla;
	}

	public void setSigla(String sigla) {
		this.sigla = sigla.toUpperCase();
	}

	public Date getDataHoraCadastro() {
		return dataHoraCadastro;
	}

	public void setDataHoraCadastro(Date dataHoraCadastro) {
		this.dataHoraCadastro = dataHoraCadastro;
	}

	public Date getDataHoraUltimaAtualizacao() {
		return dataHoraUltimaAtualizacao;
	}

	public void setDataHoraUltimaAtualizacao(Date dataHoraUltimaAtualizacao) {
		this.dataHoraUltimaAtualizacao = dataHoraUltimaAtualizacao;
	}
}