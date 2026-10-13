package com.ultimate.inventory.Repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class RepositoryManager {

    private final EntityManager entityManager;

    public RepositoryManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public <T> T find(Class<T> entity, Object id) {
        return entityManager.find(entity, id);
    }

    public <T> T save(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    public <T> T update(T entity) {
        return entityManager.merge(entity);
    }

    public void delete(Object entity) {
        entityManager.remove(entity);
    }
}
