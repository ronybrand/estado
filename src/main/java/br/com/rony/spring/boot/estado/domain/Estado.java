package br.com.rony.spring.boot.estado.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;


@Entity
@Table(name = "estado")
public class Estado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @Size(min = 2, max = 100)
    @Column(nullable = false, length = 100)
    private String nome;

    @NotNull
    @Size(min = 2, max = 2)
    @Column(nullable = false, length = 2)
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
		this.sigla = sigla;
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