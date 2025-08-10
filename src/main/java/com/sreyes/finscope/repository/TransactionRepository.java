package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Transaction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface TransactionRepository extends R2dbcRepository<Transaction, Long> {

  @Query("SELECT * FROM transactions WHERE transaction_type_id = :id")
  Flux<Transaction> findByTransactionTypeId(Long id);

  @Query("SELECT * FROM transactions WHERE category_id = :id")
  Flux<Transaction> findByCategoryId(Long categoryId);

  @Query("""
      SELECT * FROM transactions
      WHERE EXTRACT(YEAR FROM date) = :year AND EXTRACT(MONTH FROM date) = :month
      """)
  Flux<Transaction> findByMonthAndYear(int year, int month);

}

