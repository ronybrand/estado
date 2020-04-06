package br.com.rony.spring.boot.estado.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import br.com.rony.spring.boot.estado.domain.Estado;
import br.com.rony.spring.boot.estado.repository.EstadoRepository;

@RunWith(MockitoJUnitRunner.Silent.class)
public class EstadoServiceTest {
	
	@InjectMocks
	EstadoService service;
	
	@Mock
	EstadoRepository repository;
	
	private Estado getDomain(Long id, String nome, String sigla) {
		Estado domain = new Estado();
		domain.setId(id);
		domain.setNome(nome);
		domain.setSigla(sigla);
		return domain;
	}
	
	private List<Estado> getList(){
		List<Estado> retorno = new ArrayList<Estado>();
		retorno.add(this.getDomain(new Long(1), "Santa Catarina", "SC"));
		retorno.add(this.getDomain(new Long(2), "Paraná", "PR"));
		retorno.add(this.getDomain(new Long(3), "Rio Grande do Sul", "RS"));
		return retorno;
	}
 	
	@Test
	public void listar() {
		List<Estado> lista = this.getList();
		when(repository.listar()).thenReturn(lista);
		List<Estado> retorno = service.listar();
		assertEquals(lista.size(), retorno.size());
	}
	
	@Test
	public void salvar() {
		Estado domain = this.getDomain(null, "Santa Catarina", "SC");
		domain.setId(null);
		when(repository.salvar(domain)).thenReturn(domain);
		Estado retorno = service.salvar(domain);;
		assertNotNull(retorno);
	}
	
	@Test
	public void atualizar() {
		Estado domain = this.getDomain(new Long(1), "Santa Catarina", "SC");
		when(repository.atualizar(domain)).thenReturn(domain);
		Estado retorno = service.atualizar(domain);;
		assertNotNull(retorno);
	}
	

	@Test
	public void excluir() {
		Long idDomain = new Long(1);
		Estado domain = this.getDomain(idDomain, "Santa Catarina", "SC");
		domain.setId(null);
		when(repository.excluir(domain)).thenReturn(domain);
		service.excluir(idDomain);
		assertNotNull(domain);
	}

}
