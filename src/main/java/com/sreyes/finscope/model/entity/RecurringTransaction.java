package com.sreyes.finscope.model.entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa un movimiento fijo: algo que se repite mes a mes, como el
 * alquiler, el internet o el sueldo.
 * Está mapeada a la tabla `recurring_transactions` en la base de datos.
 *
 * Es una plantilla, no un movimiento. Dice que ese cargo vuelve cada cierto tiempo, no que
 * haya ocurrido: los hechos siguen siendo las {@link Transaction}. La plantilla no genera
 * nada sola; cada mes produce un pendiente que el usuario confirma, y esa confirmación es
 * la que crea la transacción y la deja enlazada aquí.
 *
 * Autogenerar sería más cómodo un día y mentiroso todos los demás: el recibo de luz no es
 * el mismo cada mes, la fecha real del cargo se corre, y un historial que se inventa
 * movimientos deja de servir para lo único que sirve, que es saber qué pasó.
 *
 * A diferencia de {@link Budget}, no está atada a un mes: vive por encima de ellos y su
 * estado en cada uno se resuelve al leer.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("recurring_transactions")
public class RecurringTransaction {

  @Id
  @Column("id_recurring")
  private Long id;

  /**
   * Identificador del usuario propietario.
   */
  @Column("user_id")
  private Long userId;

  /**
   * Categoría con la que se registrará el movimiento. Debe admitir el tipo indicado, igual
   * que al registrarlo a mano.
   */
  @Column("category_id")
  private Long categoryId;

  /**
   * Tipo del movimiento que se repite. Va en la plantilla porque un fijo también puede ser
   * un ingreso: el sueldo es lo más recurrente que existe, y es justo lo que hace falta
   * para saber cuánto queda libre en el mes.
   */
  @Column("transaction_type_id")
  private Long transactionTypeId;

  /**
   * Cómo lo llama el usuario, por ejemplo «Alquiler». Se copia al movimiento al confirmar.
   */
  private String description;

  /**
   * Lo que se suele pagar o cobrar, no lo definitivo. Al confirmar el mes se puede
   * corregir, y lo corregido vive en la transacción; aquí se conserva la estimación.
   */
  private BigDecimal amount;

  /**
   * Día previsto dentro del mes, entre 1 y 31. Se recorta a la longitud del mes al
   * calcular el vencimiento: un cargo del 31 vence el 28 en febrero.
   */
  @Column("day_of_month")
  private Integer dayOfMonth;

  /**
   * Cada cuántos meses toca, contando desde el mes de arranque: 1 mensual, 2 bimestral,
   * 3 trimestral, 12 anual. Un entero en lugar de un enum porque el seguro anual del auto
   * entra sin añadir ningún valor nuevo al dominio.
   */
  @Column("every_months")
  private Integer everyMonths;

  /**
   * Mes desde el que aplica. Sin ancla, un fijo dado de alta en septiembre aparecería como
   * impagado en enero, y además es lo que da sentido a {@link #everyMonths}: cada dos meses
   * tiene que ser cada dos meses contados desde alguno.
   */
  @Column("start_month")
  private Integer startMonth;

  @Column("start_year")
  private Integer startYear;

  /**
   * Un fijo pausado no vence en ningún mes ni compromete presupuesto, pero conserva los
   * meses en los que sí se pagó. Es lo que hay que usar al dejar de pagar algo: borrar la
   * plantilla del gimnasio pierde los seis meses en que sí se fue.
   */
  private Boolean active;
}
