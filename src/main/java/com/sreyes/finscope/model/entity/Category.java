package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa una categoría en el sistema.
 * Está mapeada a la tabla `categories` en la base de datos.
 * Contiene el identificador único y el nombre de la categoría.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("categories")
public class Category {

  @Id
  @Column("id_category")
  private Long id;

  @Column("name_category")
  private String name;
}
