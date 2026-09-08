package br.com.rony.spring.boot.estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.rony.spring.boot.estado.auth.JwtService;
import br.com.rony.spring.boot.estado.auth.SecurityConfig;

// @Import(SecurityConfig.class): @WebMvcTest NAO carrega @Configuration comuns
// por padrao (so Controller/ControllerAdvice/Filter/WebMvcConfigurer/etc) -
// sem esse import, authorizeHttpRequests nunca roda neste slice e um teste
// sem token passaria como se estivesse autenticado (achado testando
// excluirSemAutenticacaoRetorna401, que falhava silenciosamente com 204 antes
// deste import). @TestPropertySource supre admin.password-hash/jwt.secret,
// que nao tem default em application.yml (ADR 0017) - sem isso, o bind de
// @ConfigurationProperties que o SecurityConfig habilita falha o contexto.
@WebMvcTest(EstadoController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"admin.password-hash=teste", "jwt.secret=segredo-de-teste-com-32-bytes-ou-mais"})
public class EstadoControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	EstadoService service;

	@MockitoBean
	JwtService jwtService;

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
	public void getPaginadoRetornaPageDeEstadoDTO() throws Exception {
		Pageable pageable = PageRequest.of(0, 10);
		List<Estado> lista = List.of(this.getDomain(1L, "Santa Catarina", "SC"));
		when(service.listarPaginado(any(Pageable.class))).thenReturn(new PageImpl<>(lista, pageable, lista.size()));

		mockMvc.perform(get("/estado/paginado"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].sigla").value("SC"))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	public void getPaginadoComSizeAcimaDoLimiteClampaParaOMaximoConfigurado() throws Exception {
		// app.pagination.max-size (application.yml) limita o tamanho de pagina
		// no servidor independente do que o cliente pedir - o
		// PageableHandlerMethodArgumentResolver do Spring Data clampa
		// silenciosamente pro maximo, nao rejeita com 400.
		Pageable pageable = PageRequest.of(0, 100);
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		when(service.listarPaginado(captor.capture())).thenReturn(new PageImpl<>(List.of(), pageable, 0));

		mockMvc.perform(get("/estado/paginado").param("size", "500"))
				.andExpect(status().isOk());

		assertEquals(100, captor.getValue().getPageSize());
	}

	@Test
	public void getPaginadoComSortInvalidoRetorna400() throws Exception {
		// achado via EstadoRepositoryIT contra Postgres real: sort=campo-que-nao-existe
		// nao e barrado pelo Pageable (so page/size sao validados ali) - so estoura
		// quando o Hibernate resolve a propriedade, como PropertyReferenceException.
		// Sem o handler dedicado (CustomGlobalExceptionHandler), isso cai no catch-all
		// e vira 500 pra um input de cliente invalido.
		when(service.listarPaginado(any(Pageable.class)))
				.thenThrow(new PropertyReferenceException("campoInexistente",
						TypeInformation.of(Estado.class), List.of()));

		mockMvc.perform(get("/estado/paginado").param("sort", "campoInexistente,asc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Parametro de ordenacao invalido"));
	}

	@Test
	public void salvarComPayloadValidoRetorna201ComEstadoDTOCriado() throws Exception {
		Estado salvo = this.getDomain(1L, "Santa Catarina", "SC");
		when(service.salvar(any(Estado.class))).thenReturn(salvo);

		mockMvc.perform(post("/estado")
				.with(user("admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.sigla").value("SC"));
	}

	@Test
	public void salvarSemSiglaRetorna400ComFormatoDeErroDaApi() throws Exception {
		mockMvc.perform(post("/estado")
				.with(user("admin"))
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

		mockMvc.perform(put("/estado/1")
				.with(user("admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.nome").value("Santa Catarina"));
	}

	@Test
	public void atualizarUsaOIdDoPathNaEntidadeEnviadaAoService() throws Exception {
		// id vem so da URL agora (PUT /estado/{id}), nao mais do corpo - ver
		// EstadoUpdateRequestDTO pro motivo da mudanca.
		ArgumentCaptor<Estado> captor = ArgumentCaptor.forClass(Estado.class);
		when(service.atualizar(captor.capture())).thenReturn(this.getDomain(42L, "Santa Catarina", "SC"));

		mockMvc.perform(put("/estado/42")
				.with(user("admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isOk());

		assertEquals(Long.valueOf(42), captor.getValue().getId());
	}

	@Test
	public void atualizarComIdNegativoRetorna400() throws Exception {
		mockMvc.perform(put("/estado/-1")
				.with(user("admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isBadRequest());
		verify(service, never()).atualizar(any(Estado.class));
	}

	@Test
	public void atualizarComIdZeroRetorna400() throws Exception {
		mockMvc.perform(put("/estado/0")
				.with(user("admin"))
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
				.with(user("admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":999,\"nome\":\"Santa Catarina\",\"sigla\":\"SC\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/estado/1"));

		assertNull(captor.getValue().getId());
	}

	@Test
	public void excluirRetorna204() throws Exception {
		mockMvc.perform(delete("/estado/1").with(user("admin"))).andExpect(status().isNoContent());
	}

	@Test
	public void excluirComIdNegativoRetorna400() throws Exception {
		// achado de code review: excluir() nao tinha @Positive/@Max, diferente
		// do get() irmao - o mesmo input invalido respondia 404 no DELETE e
		// 400 no GET, um contrato inconsistente entre endpoints do mesmo
		// recurso.
		mockMvc.perform(delete("/estado/-1").with(user("admin"))).andExpect(status().isBadRequest());
		verify(service, never()).excluir(anyLong());
	}

	@Test
	public void excluirComIdZeroRetorna400() throws Exception {
		mockMvc.perform(delete("/estado/0").with(user("admin"))).andExpect(status().isBadRequest());
	}

	@Test
	public void excluirSemAutenticacaoRetorna401() throws Exception {
		mockMvc.perform(delete("/estado/1")).andExpect(status().isUnauthorized());
		verify(service, never()).excluir(anyLong());
	}

	@Test
	public void getComOrigemPermitidaEcoaOAccessControlAllowOrigin() throws Exception {
		when(service.getDomainById(1L)).thenReturn(this.getDomain(1L, "Santa Catarina", "SC"));

		mockMvc.perform(get("/estado/1").header("Origin", "http://localhost:8000"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8000"));
	}

	@Test
	public void getComOrigemNaoPermitidaRetorna403() throws Exception {
		// achado ao investigar o deploy do react-state: WebConfig.originPermitida
		// virou lista pra suportar mais de uma origem (ver ApiProperty) - este
		// teste prova que uma origem fora da lista continua sendo rejeitada,
		// nao so que a permitida funciona.
		mockMvc.perform(get("/estado/1").header("Origin", "https://origem-nao-permitida.example.com"))
				.andExpect(status().isForbidden());
		verify(service, never()).getDomainById(anyLong());
	}
}
