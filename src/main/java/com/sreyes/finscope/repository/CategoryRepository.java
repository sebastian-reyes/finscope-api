package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Category;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para la entidad {@link Category}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `categories`.
 * Extiende {@link R2dbcRepository} para soporte CRUD.
 */
@Repository
public interface CategoryRepository extends R2dbcRepository<Category, Long> {
}
