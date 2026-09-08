package br.com.rony.spring.boot.estado;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping( value = "/estado")
@Tag(name = "Estado", description = "CRUD das unidades federativas do Brasil")
public class EstadoController {
	private final EstadoService service;

	// Sem validacao manual de page/size de proposito: o PageableHandlerMethodArgumentResolver
	// do Spring Data ja trata os dois como dica, nao input critico - um valor nao numerico ou
	// fora dos limites e clampado silenciosamente pro default/max (ver pagination.max-size em
	// application.yml), nunca lanca excecao. sort e diferente e ja tem tratamento dedicado: um
	// campo inexistente so estoura na execucao da query (PropertyReferenceException), mapeado
	// pra 400 em CustomGlobalExceptionHandler.
	@Operation(summary = "Lista estados paginados",
			description = "Suporta ?page, ?size (limitado por app.pagination.max-size) e ?sort=campo,asc|desc.")
	@ApiResponse(responseCode = "200", description = "Pagina de estados retornada com sucesso")
	@ApiResponse(responseCode = "400", description = "Campo de sort inexistente na entidade")
	@GetMapping("/paginado")
	public Page<EstadoDTO> getPaginado(Pageable pageable) {
		return service.listarPaginado(pageable).map(EstadoDTO::from);
	}

	@Operation(summary = "Busca um estado por id")
	@ApiResponse(responseCode = "200", description = "Estado encontrado")
	@ApiResponse(responseCode = "400", description = "Id invalido (nao positivo)")
	@ApiResponse(responseCode = "404", description = "Estado nao encontrado")
	@GetMapping("/{id}")
	public EstadoDTO get(@Parameter(description = "Id do estado") @Positive @Max(Integer.MAX_VALUE)
			@PathVariable("id") Long idDomain) {
		return EstadoDTO.from(service.getDomainById(idDomain));
	}

	@Operation(summary = "Cria um novo estado", security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "201", description = "Estado criado")
	@ApiResponse(responseCode = "400", description = "Payload invalido (nome/sigla ausentes ou fora do tamanho)")
	@ApiResponse(responseCode = "401", description = "Token ausente ou invalido")
	@ApiResponse(responseCode = "409", description = "Nome ou sigla ja cadastrados")
	@PostMapping
	public ResponseEntity<EstadoDTO> salvar(@Valid @RequestBody EstadoCreateRequestDTO estado) {
		Estado salvo = service.salvar(estado.toEntity());

		return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(salvo.getId()).toUri()).body(EstadoDTO.from(salvo));
	}

	@Operation(summary = "Atualiza um estado existente", security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "200", description = "Estado atualizado")
	@ApiResponse(responseCode = "400", description = "Id invalido ou payload invalido")
	@ApiResponse(responseCode = "401", description = "Token ausente ou invalido")
	@ApiResponse(responseCode = "404", description = "Estado nao encontrado")
	@PutMapping("/{id}")
	public ResponseEntity<EstadoDTO> atualizar(@Parameter(description = "Id do estado") @Positive
			@Max(Integer.MAX_VALUE) @PathVariable("id") Long id,
			@Valid @RequestBody EstadoUpdateRequestDTO estado) {
		Estado atualizado = service.atualizar(estado.toEntity(id));

		return ResponseEntity.status(HttpStatus.OK).body(EstadoDTO.from(atualizado));
	}

	@Operation(summary = "Exclui um estado", security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "204", description = "Estado excluido")
	@ApiResponse(responseCode = "400", description = "Id invalido (nao positivo)")
	@ApiResponse(responseCode = "401", description = "Token ausente ou invalido")
	@ApiResponse(responseCode = "404", description = "Estado nao encontrado")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void excluir(@Parameter(description = "Id do estado") @Positive @Max(Integer.MAX_VALUE)
			@PathVariable("id") Long id) {
		service.excluir(id);
	}
}
