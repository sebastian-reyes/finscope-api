package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que enlaza una transacción con uno de sus tags.
 * Está mapeada a la tabla `transaction_tags` en la base de datos y materializa la relación
 * muchos a muchos entre {@link Transaction} y {@link Tag}.
 * Lleva clave subrogada, aunque el par transacción-tag ya sería único por sí mismo, porque
 * el repositorio reactivo necesita un identificador para insertar; la unicidad real la
 * garantiza la restricción `uq_transaction_tags` de la base de datos.
 * El enlace desaparece con su transacción, pero el tag sobrevive en el catálogo del
 * usuario y vuelve a usarse si escribe el mismo nombre más adelante.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("transaction_tags")
public class TransactionTag {

  @Id
  @Column("id_transaction_tag")
  private Long id;

  @Column("transaction_id")
  private Long transactionId;

  @Column("tag_id")
  private Long tagId;
}
