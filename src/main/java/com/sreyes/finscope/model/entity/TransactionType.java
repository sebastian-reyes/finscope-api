package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa un tipo de transacción en el sistema.
 * Está mapeada a la tabla `transaction_types` en la base de datos.
 * Es un catálogo global y cerrado de dos elementos, ingreso y egreso, porque de él depende
 * el signo del importe de la transacción.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("transaction_types")
public class TransactionType {

  @Id
  @Column("id_transaction_type")
  private Long id;

  @Column("name_transaction_type")
  private String name;

  /**
   * Código estable del tipo, INCOME o EXPENSE. Permite que los clientes no dependan de
   * los identificadores numéricos del catálogo.
   */
  @Column("code")
  private String code;
}
