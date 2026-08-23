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
 * El importe se guarda siempre en positivo; el signo lo aporta el tipo de transacción,
 * que indica si se trata de un ingreso o de un egreso.
 * Sus tags se almacenan en la tabla `tags`, que depende de esta.
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

  /**
   * Identificador del usuario propietario. Aisla los datos entre cuentas.
   */
  @Column("user_id")
  private Long userId;

  @Column("transaction_type_id")
  private Long transactionTypeId;

}
