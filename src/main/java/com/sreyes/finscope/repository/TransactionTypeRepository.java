package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.TransactionType;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionTypeRepository extends R2dbcRepository<TransactionType, Long> {
}
