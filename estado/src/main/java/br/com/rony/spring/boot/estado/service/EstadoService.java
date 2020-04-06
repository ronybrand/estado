package br.com.rony.spring.boot.estado.service;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import br.com.rony.spring.boot.estado.domain.Estado;
import br.com.rony.spring.boot.estado.repository.EstadoRepository;

@Service
@Transactional
public class EstadoService {

    @Autowired
    private EstadoRepository repository;

    
    public Estado salvar(Estado domain) {
        return repository.salvar(domain);
    }

    
    public Estado atualizar(Estado domain) {
    	return repository.atualizar(domain);
    }

    
    public void excluir(long idDomain) {
    	repository.excluir(new Estado(idDomain));
    }
    
    public List<Estado> listar() {
    	return repository.listar();
    }
    
    public Estado getDomainById(long idDomain) {
    	return repository.getDomainById(idDomain);
    }
}