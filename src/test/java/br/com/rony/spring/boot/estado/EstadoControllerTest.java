package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EstadoController.class)
public class EstadoControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	EstadoService service;

	private Estado getDomain(Long id, String nome, String sigla) {
		Estado domain = new Estado();
		domain.setId(id);
		domain.setNome(nome);
		domain.setSigla(sigla);
		domain.setDataHoraCadastro(LocalDateTime.now());
		return domain;
	}

	@Test
	public void getComIdPositivoValidoRetorna200() throws Exception {
		when(service.getDomainById(1L)).thenReturn(this.getDomain(1L, "Santa Catarina", "SC"));

		mockMvc.perform(get("/estado/1")).andExpect(status().isOk());
	}

	@Test
	public void getComIdNegativoRetorna400() throws Exception {
		mockMvc.perform(get("/estado/-1")).andExpect(status().isBadRequest());
		verify(service, never()).getDomainById(anyLong());
	}

	@Test
	public void getComIdZeroRetorna400() throws Exception {
		mockMvc.perform(get("/estado/0")).andExpect(status().isBadRequest());
	}

	@Test
	public void getComIdAcimaDoLimiteSuperiorRetorna400() throws Exception {
		mockMvc.perform(get("/estado/9999999999")).andExpect(status().isBadRequest());
	}

	@Test
	public void getRetornaEstadoDTOComOsDadosDaEntidade() throws Exception {
		Estado domain = this.getDomain(1L, "Santa Catarina", "SC");
		when(service.getDomainById(1L)).thenReturn(domain);

		mockMvc.perform(get("/estado/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.nome").value("Santa Catarina"))
				.andExpect(jsonPath("$.sigla").value("SC"));
	}

	@Test
	public void getAllRetornaListaDeEstadoDTO() throws Exception {
		when(service.listar()).thenReturn(List.of(this.getDomain(1L, "Santa Catarina", "SC")));

		mockMvc.perform(get("/estado"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].sigla").value("SC"));
	}

	@Test
	public void salvarComPayloadValidoRetorna201ComEstadoDTOCriado() throws Exception {
		Estado salvo = this.getDomain(1L, "Santa Catarina", "SC");
		when(service.salvar(any(Estado.class))).thenReturn(salvo);

		mockMvc.perform(post("/estado")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.sigla").value("SC"));
	}

	@Test
	public void salvarSemSiglaRetorna400ComFormatoDeErroDaApi() throws Exception {
		mockMvc.perform(post("/estado")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Santa Catarina\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sigla")));
		verify(service, never()).salvar(any(Estado.class));
	}

	@Test
	public void atualizarComPayloadValidoRetorna200ComEstadoDTOAtualizado() throws Exception {
		Estado atualizado = this.getDomain(1L, "Santa Catarina", "SC");
		when(service.atualizar(any(Estado.class))).thenReturn(atualizado);

		mockMvc.perform(put("/estado")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":1,\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.nome").value("Santa Catarina"));
	}

	@Test
	public void atualizarSemIdRetorna400EmVezDeNullPointerException() throws Exception {
		// ver EstadoUpdateRequestDTO pro motivo.
		mockMvc.perform(put("/estado")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isBadRequest());
		verify(service, never()).atualizar(any(Estado.class));
	}

	@Test
	public void salvarComIdNoPayloadIgnoraOIdEnviado() throws Exception {
		// ver EstadoCreateRequestDTO pro motivo.
		ArgumentCaptor<Estado> captor = ArgumentCaptor.forClass(Estado.class);
		when(service.salvar(captor.capture())).thenReturn(this.getDomain(1L, "Santa Catarina", "SC"));

		mockMvc.perform(post("/estado")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":999,\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isCreated());

		assertNull(captor.getValue().getId());
	}

	@Test
	public void excluirRetorna204() throws Exception {
		mockMvc.perform(delete("/estado/1")).andExpect(status().isNoContent());
	}

	@Test
	public void excluirComIdNegativoRetorna400() throws Exception {
		// achado de code review: excluir() nao tinha @Positive/@Max, diferente
		// do get() irmao - o mesmo input invalido respondia 404 no DELETE e
		// 400 no GET, um contrato inconsistente entre endpoints do mesmo
		// recurso.
		mockMvc.perform(delete("/estado/-1")).andExpect(status().isBadRequest());
		verify(service, never()).excluir(anyLong());
	}

	@Test
	public void excluirComIdZeroRetorna400() throws Exception {
		mockMvc.perform(delete("/estado/0")).andExpect(status().isBadRequest());
	}
}
