package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa el mes en que un movimiento fijo no toca.
 * Está mapeada a la tabla `recurring_skips` en la base de datos.
 *
 * Sin ella, un mes que no se paga se queda en rojo hasta que el mes acaba y sigue contando
 * como comprometido contra el presupuesto de su categoría, que es justo lo que no es: lo
 * que no se va a pagar no debería estar reservando dinero.
 *
 * Se guarda la excepción y no un estado en la plantilla porque la omisión es de un mes
 * concreto y la plantilla vive por encima de los meses.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("recurring_skips")
public class RecurringSkip {

  @Id
  @Column("id_recurring_skip")
  private Long id;

  @Column("recurring_id")
  private Long recurringId;

  /**
   * Mes que se omite, entre 1 y 12.
   */
  private Integer month;

  private Integer year;
}
