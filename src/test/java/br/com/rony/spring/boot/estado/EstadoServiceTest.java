package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
		retorno.add(this.getDomain(Long.valueOf(1), "Santa Catarina", "SC"));
		retorno.add(this.getDomain(Long.valueOf(2), "Paraná", "PR"));
		retorno.add(this.getDomain(Long.valueOf(3), "Rio Grande do Sul", "RS"));
		return retorno;
	}

	@Test
	public void listar() {
		List<Estado> lista = this.getList();
		when(repository.findAll()).thenReturn(lista);
		List<Estado> retorno = service.listar();
		assertEquals(lista.size(), retorno.size());
	}

	@Test
	public void salvar() {
		Estado domain = this.getDomain(null, "Santa Catarina", "SC");
		when(repository.save(domain)).thenReturn(domain);
		Estado retorno = service.salvar(domain);
		assertNotNull(retorno);
	}

	@Test
	public void atualizar() {
		Estado domain = this.getDomain(Long.valueOf(1), "Santa Catarina", "SC");
		when(repository.findById(domain.getId())).thenReturn(Optional.of(domain));
		when(repository.save(domain)).thenReturn(domain);
		Estado retorno = service.atualizar(domain);
		assertNotNull(retorno);
	}

	@Test
	public void excluir() {
		Long idDomain = Long.valueOf(1);
		when(repository.existsById(idDomain)).thenReturn(true);
		service.excluir(idDomain);
		verify(repository).deleteById(idDomain);
	}

	@Test
	public void getDomainById() {
		Long idDomain = Long.valueOf(1);
		Estado domain = this.getDomain(idDomain, "Santa Catarina", "SC");
		when(repository.findById(idDomain)).thenReturn(Optional.of(domain));
		Estado retorno = service.getDomainById(idDomain);
		assertEquals(domain, retorno);
	}

	@Test
	public void getDomainByIdQuandoNaoExisteLancaEstadoNaoEncontrado() {
		Long idDomain = Long.valueOf(999);
		when(repository.findById(idDomain)).thenReturn(Optional.empty());

		assertThrows(EstadoNaoEncontradoException.class, () -> service.getDomainById(idDomain));
	}

	@Test
	public void atualizarQuandoIdNaoExisteLancaEstadoNaoEncontrado() {
		Estado domain = this.getDomain(Long.valueOf(999), "Santa Catarina", "SC");
		when(repository.findById(domain.getId())).thenReturn(Optional.empty());

		assertThrows(EstadoNaoEncontradoException.class, () -> service.atualizar(domain));
	}

	@Test
	public void excluirQuandoIdNaoExisteLancaEstadoNaoEncontrado() {
		Long idDomain = Long.valueOf(999);
		when(repository.existsById(idDomain)).thenReturn(false);

		assertThrows(EstadoNaoEncontradoException.class, () -> service.excluir(idDomain));
		verify(repository, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	public void excluirQuandoIdExisteChamaDeleteByIdComOId() {
		// achado de code review: excluir() buscava a linha inteira (findById)
		// so pra confirmar existencia antes do delete - existsById +
		// deleteById evita o SELECT de colunas nunca lidas.
		Long idDomain = Long.valueOf(1);
		when(repository.existsById(idDomain)).thenReturn(true);

		service.excluir(idDomain);

		verify(repository).deleteById(idDomain);
		verify(repository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
	}
}
