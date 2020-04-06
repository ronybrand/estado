package br.com.rony.spring.boot.estado.resource;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.rony.spring.boot.estado.domain.Estado;
import br.com.rony.spring.boot.estado.service.EstadoService;

@RestController
@Validated
@RequestMapping( value = "/estado")
public class EstadoResource {
	@Autowired
	private EstadoService service;


	@RequestMapping(value = { "" , "/" } , method = RequestMethod.GET)
	public List<Estado> getAll() {
	      return service.listar();
	}
	
	@GetMapping("/{id}")
	public Estado get(@Valid @PathVariable("id") Long idDomain) { 
		return service.getDomainById(idDomain);
    }
	
	@PostMapping
	public @ResponseBody ResponseEntity < String > salvar(@Valid @RequestBody Estado estado) { 
    	service.salvar(estado);
    	
    	return ResponseEntity.status(HttpStatus.CREATED).build();
    }
	
	@PutMapping
	public @ResponseBody ResponseEntity < String > atualizar(@Valid @RequestBody Estado estado) { 
    	service.atualizar(estado);

    	return ResponseEntity.status(HttpStatus.OK).build();
    }

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable("id") Long id) {
    	service.excluir(id);
    }
}