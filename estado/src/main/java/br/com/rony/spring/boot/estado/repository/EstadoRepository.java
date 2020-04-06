package br.com.rony.spring.boot.estado.repository;


import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import br.com.rony.spring.boot.estado.domain.Estado;


@Repository
public class EstadoRepository {

    @PersistenceContext
    private EntityManager em;
    
    public Estado salvar(Estado domain) {
        em.persist(domain);
        em.flush();
        return domain;
    }

    
    public Estado atualizar(Estado domain) {
        em.merge(domain);
        em.flush();
        return domain;
    }

    
    public Estado excluir(Estado domain) {
        em.remove(em.getReference(Estado.class, domain.getId()));
        em.flush();
        return domain;
    }
    
    public List<Estado> listar(){
        return em.createQuery("select e from Estado e", Estado.class)
                .getResultList();
    }
}
