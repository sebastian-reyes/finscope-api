package com.sreyes.finscope.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa dinero que el usuario prestó a otra persona.
 * Está mapeada a la tabla `loans` en la base de datos.
 *
 * No es un movimiento. Prestar no es gastar ni cobrar es ingresar: si el préstamo entrara
 * como {@link Transaction}, el análisis del mes contaría como gasto un dinero que sigue
 * siendo del usuario. Por eso vive aparte y nunca escribe ninguna transacción.
 *
 * Lo que se acordó devolver está en sus {@link LoanInstallment} y lo que de verdad se
 * devolvió en sus {@link LoanPayment}. Lo que falta y el estado no se guardan: se calculan
 * al leer comparando las dos listas.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("loans")
public class Loan {

  @Id
  @Column("id_loan")
  private Long id;

  /**
   * Identificador del usuario que prestó el dinero.
   */
  @Column("user_id")
  private Long userId;

  /**
   * A quién se le prestó. Texto libre y no un catálogo: casi siempre son pocas personas y
   * conocidas, y un catálogo obligaría a darlas de alta antes de prestar.
   */
  @Column("borrower_name")
  private String borrowerName;

  /**
   * Lo que se prestó, siempre mayor que cero.
   */
  @Column("principal")
  private BigDecimal principal;

  /**
   * Lo que se gana encima, como monto fijo. Cero en un préstamo sin interés.
   */
  @Column("interest")
  private BigDecimal interest;

  /**
   * Moneda del préstamo, en código ISO 4217. Las cuotas y los pagos van en la misma.
   */
  @Column("currency")
  private String currency;

  /**
   * Día en que se entregó el dinero. Ninguna cuota puede vencer antes.
   */
  @Column("lent_on")
  private LocalDate lentOn;

  @Column("notes")
  private String notes;

  /**
   * Día en que se dio por cerrado sin que se devolviera todo, o nulo si sigue abierto.
   * Un préstamo cerrado deja de avisar.
   */
  @Column("closed_on")
  private LocalDate closedOn;
}
