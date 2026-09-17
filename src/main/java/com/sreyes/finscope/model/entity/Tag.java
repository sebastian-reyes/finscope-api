package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa un tag del usuario.
 * Está mapeada a la tabla `tags` en la base de datos.
 * El tag es texto libre y pertenece al usuario, no a una transacción concreta: se crea al
 * vuelo al escribirlo dentro de una transacción, de modo que clasificar sigue sin obligar
 * a mantener un catálogo previo. La relación con las transacciones vive en
 * {@link TransactionTag}, porque un tag puede usarse en muchas y una transacción puede
 * llevar muchos.
 * Un mismo nombre no puede repetirse para un usuario sin distinguir mayúsculas, lo que
 * impide que `Casa` y `casa` acaben siendo dos tags distintos en el autocompletado.
 * Los tags no intervienen en el cálculo monetario.
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
   * Identificador del usuario propietario del tag.
   */
  @Column("user_id")
  private Long userId;

  @Column("name_tag")
  private String name;

  /**
   * Color elegido para su ficha: `preset-0` a `preset-7` o `#rrggbb`. Nulo mientras no se
   * elija, que es cuando el cliente lo deduce del nombre.
   */
  @Column("color")
  private String color;

  /**
   * Icono elegido para su ficha, sin prefijo (`basket`). Nulo mientras no se elija, que es
   * cuando el cliente lo deduce del nombre.
   */
  @Column("icon")
  private String icon;
}
