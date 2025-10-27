package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.TransactionType;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para la entidad {@link TransactionType}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `transaction_types`.
 * Extiende {@link R2dbcRepository} para soporte CRUD.
 */
@Repository
public interface TransactionTypeRepository extends R2dbcRepository<TransactionType, Long> {
}
