package com.sreyes.finscope.model.entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa el presupuesto de una categoría para un mes.
 * Está mapeada a la tabla `budgets` en la base de datos.
 *
 * Es la única pieza del modelo que mira hacia delante: dice cuánto se piensa gastar, no
 * cuánto se gastó. Por eso vive aparte de {@link Transaction} y no se toca cuando un
 * movimiento se registra, se corrige o se borra.
 *
 * La unidad es el mes natural, la misma con la que se lee un sueldo y con la que abre el
 * resumen, y cada categoría tiene como mucho un presupuesto en cada mes.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("budgets")
public class Budget {

  @Id
  @Column("id_budget")
  private Long id;

  /**
   * Identificador del usuario propietario del presupuesto.
   */
  @Column("user_id")
  private Long userId;

  /**
   * Categoría presupuestada. Debe admitir egresos: el avance se mide contra lo gastado,
   * de modo que una categoría de solo ingresos no tendría nada contra lo que compararse.
   */
  @Column("category_id")
  private Long categoryId;

  /**
   * Mes al que se aplica, entre 1 y 12.
   */
  @Column("month")
  private Integer month;

  /**
   * Año al que se aplica.
   */
  @Column("year")
  private Integer year;

  /**
   * Importe presupuestado, siempre mayor que cero.
   */
  @Column("amount")
  private BigDecimal amount;
}
