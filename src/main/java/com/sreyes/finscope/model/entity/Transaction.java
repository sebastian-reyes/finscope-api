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
 * Lleva exactamente una categoría, que es lo que dice en qué se gastó y permite repartir
 * el total del periodo sin contar nada dos veces, y además cero o varios tags, que dicen
 * en qué contexto ocurrió y viven en la tabla de enlace `transaction_tags`.
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

  /**
   * Categoría principal. Es obligatoria: sin ella el reparto del gasto por categoría
   * dejaría transacciones fuera y no sumaría el total del periodo.
   */
  @Column("category_id")
  private Long categoryId;

}
