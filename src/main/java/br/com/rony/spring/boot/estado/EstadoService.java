package br.com.rony.spring.boot.estado;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class EstadoService {

    private final EstadoRepository repository;

    public Estado salvar(Estado domain) {
    	domain.setDataHoraCadastro(LocalDateTime.now(ZoneOffset.UTC));
        Estado salvo = repository.save(domain);
        log.info("Estado criado: id={} sigla={}", salvo.getId(), salvo.getSigla());
        return salvo;
    }


    public Estado atualizar(Estado domain) {
    	Estado domainBD = this.getDomainById(domain.getId());
    	domainBD.setNome(domain.getNome());
    	domainBD.setSigla(domain.getSigla());
    	domainBD.setDataHoraUltimaAtualizacao(LocalDateTime.now(ZoneOffset.UTC));
    	Estado atualizado = repository.save(domainBD);
    	log.info("Estado atualizado: id={} sigla={}", atualizado.getId(), atualizado.getSigla());
    	return atualizado;
    }


    public void excluir(long idDomain) {
    	Estado domainBD = this.getDomainById(idDomain);
    	repository.delete(domainBD);
    	log.info("Estado excluido: id={}", idDomain);
    }

    public List<Estado> listar() {
    	return repository.findAll();
    }

    public Estado getDomainById(long idDomain) {
    	return repository.findById(idDomain)
    			.orElseThrow(() -> new EstadoNaoEncontradoException("Estado nao encontrado: id=" + idDomain));
    }
}
