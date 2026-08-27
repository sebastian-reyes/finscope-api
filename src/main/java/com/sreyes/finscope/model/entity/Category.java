package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa una categoría del usuario.
 * Está mapeada a la tabla `categories` en la base de datos.
 *
 * La categoría dice en qué se gastó el dinero y cada transacción tiene exactamente una,
 * que es lo que la diferencia de {@link Tag}: al no poder repetirse dentro de una misma
 * transacción, la suma de los importes por categoría coincide con el total del periodo y
 * puede repartirse en porcentajes. Los tags, que son varios, no pueden hacerlo.
 *
 * El catálogo pertenece al usuario y es suyo para editarlo: se siembra al registrarse con
 * un juego inicial y desde ahí puede añadir, renombrar y borrar. Un mismo nombre no puede
 * repetirse dentro de una cuenta sin distinguir mayúsculas, igual que en los tags.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("categories")
public class Category {

  @Id
  @Column("id_category")
  private Long id;

  /**
   * Identificador del usuario propietario de la categoría.
   */
  @Column("user_id")
  private Long userId;

  @Column("name_category")
  private String name;

  /**
   * Tipo de movimiento al que se ofrece la categoría: EXPENSE, INCOME o BOTH.
   * Solo decide qué categorías propone el formulario de registro; lo ya guardado no se
   * toca aunque después cambie.
   */
  @Column("applies_to")
  private String appliesTo;

  /**
   * Marca la categoría de reserva del usuario, la que recibe las transacciones de las
   * categorías que se eliminan. Existe una sola por cuenta y no puede borrarse: sin ella,
   * eliminar una categoría dejaría transacciones sin la suya, que es obligatoria.
   */
  @Column("is_system")
  private boolean system;
}
