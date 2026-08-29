package br.com.rony.spring.boot.estado;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

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

import lombok.RequiredArgsConstructor;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping( value = "/estado")
public class EstadoController {
	private final EstadoService service;

	@GetMapping
	public List<EstadoDTO> getAll() {
		return service.listar().stream().map(EstadoDTO::from).collect(Collectors.toList());
	}

	@GetMapping("/{id}")
	public EstadoDTO get(@Positive @Max(Integer.MAX_VALUE) @PathVariable("id") Long idDomain) {
		return EstadoDTO.from(service.getDomainById(idDomain));
    }

	@PostMapping
	public ResponseEntity<EstadoDTO> salvar(@Valid @RequestBody EstadoCreateRequestDTO estado) {
    	Estado salvo = service.salvar(estado.toEntity());

    	return ResponseEntity.status(HttpStatus.CREATED).body(EstadoDTO.from(salvo));
    }

	@PutMapping
	public ResponseEntity<EstadoDTO> atualizar(@Valid @RequestBody EstadoUpdateRequestDTO estado) {
    	Estado atualizado = service.atualizar(estado.toEntity());

    	return ResponseEntity.status(HttpStatus.OK).body(EstadoDTO.from(atualizado));
    }

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@Positive @Max(Integer.MAX_VALUE) @PathVariable("id") Long id) {
    	service.excluir(id);
    }
}
