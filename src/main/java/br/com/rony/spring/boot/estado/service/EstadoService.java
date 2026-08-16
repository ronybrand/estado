package br.com.rony.spring.boot.estado.service;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rony.spring.boot.estado.domain.Estado;
import br.com.rony.spring.boot.estado.repository.EstadoRepository;

@Service
@Transactional
public class EstadoService {

    private static final Logger log = LoggerFactory.getLogger(EstadoService.class);

    private final EstadoRepository repository;

    public EstadoService(EstadoRepository repository) {
        this.repository = repository;
    }


    public Estado salvar(Estado domain) {
    	domain.setDataHoraCadastro(new Date());
        Estado salvo = repository.salvar(domain);
        log.info("Estado criado: id={} sigla={}", salvo.getId(), salvo.getSigla());
        return salvo;
    }


    public Estado atualizar(Estado domain) {
    	Estado domainBD = this.getDomainById(domain.getId());
    	domainBD.setNome(domain.getNome());
    	domainBD.setSigla(domain.getSigla());
    	domainBD.setDataHoraUltimaAtualizacao(new Date());
    	Estado atualizado = repository.atualizar(domainBD);
    	log.info("Estado atualizado: id={} sigla={}", atualizado.getId(), atualizado.getSigla());
    	return atualizado;
    }


    public void excluir(long idDomain) {
    	repository.excluir(new Estado(idDomain));
    	log.info("Estado excluido: id={}", idDomain);
    }
    
    public List<Estado> listar() {
    	return repository.listar();
    }
    
    public Estado getDomainById(long idDomain) {
    	return repository.getDomainById(idDomain);
    }
}