package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa un tag de una transacción.
 * Está mapeada a la tabla `tags` en la base de datos.
 * Un tag es texto libre y pertenece a una única transacción, de modo que clasificar no
 * obliga a mantener un catálogo previo. El mismo nombre puede repetirse entre
 * transacciones distintas, pero no dentro de una misma.
 * Los tags no intervienen en el cálculo monetario y desaparecen con su transacción.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("tags")
public class Tag {

  @Id
  @Column("id_tag")
  private Long id;

  /**
   * Identificador de la transacción a la que pertenece el tag.
   */
  @Column("transaction_id")
  private Long transactionId;

  @Column("name_tag")
  private String name;
}
