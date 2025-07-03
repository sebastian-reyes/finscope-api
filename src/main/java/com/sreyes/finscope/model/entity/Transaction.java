package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

  @Id
  @Column("id_transaction")
  private Long id;

  private BigDecimal amount;

  private String description;

  private LocalDateTime date;

  @Column("category_id")
  private Long categoryId;

  @Column("transaction_type_id")
  private Long transactionTypeId;
}