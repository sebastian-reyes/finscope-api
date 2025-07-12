package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.dto.TransactionResponseDTO;
import com.sreyes.finscope.model.entity.Transaction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface TransactionRepository extends R2dbcRepository<Transaction, Long> {

  @Query("SELECT * FROM transactions WHERE transaction_type_id = :id")
  Flux<Transaction> findByTransactionType(Long id);

  @Query("""
      SELECT t.id_transaction, t.amount, t.description, t.date,
             c.id_category, c.name_category,
             tt.id_transaction_type, tt.name_transaction_type
      FROM transactions t
      JOIN categories c ON t.category_id = c.id_category
      JOIN transaction_types tt ON t.transaction_type_id = tt.id_transaction_type
      WHERE t.transaction_type_id = :id
      """)
  Flux<TransactionResponseDTO> findTransactionsByTypeId(Long id);

}
