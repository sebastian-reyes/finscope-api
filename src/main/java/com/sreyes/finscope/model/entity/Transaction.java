package com.sreyes.finscope.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa una transacción en el sistema.
 * Está mapeada a la tabla `transactions` en la base de datos.
 * Contiene información como el identificador, monto, descripción,
 * fecha, identificador de categoría y tipo de transacción.
 */
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