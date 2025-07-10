package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Transaction;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface TransactionRepository extends R2dbcRepository<Transaction, Long> {
}
