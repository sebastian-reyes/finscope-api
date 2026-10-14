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
 * Entidad que representa una cuota del plan de devolución de un {@link Loan}.
 * Está mapeada a la tabla `loan_installments` en la base de datos.
 *
 * Es lo que se acordó, no lo que pasó: no lleva ninguna marca de pagada. Cuánto la cubre se
 * calcula al leer aplicando los pagos en orden de cuota, y así un pago parcial o uno
 * adelantado no obligan a decidir a qué cuota corresponden.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("loan_installments")
public class LoanInstallment {

  @Id
  @Column("id_loan_installment")
  private Long id;

  @Column("loan_id")
  private Long loanId;

  /**
   * Orden de la cuota en el plan, desde 1. Decide a qué cuota se aplica cada pago.
   */
  @Column("number")
  private Integer number;

  @Column("due_date")
  private LocalDate dueDate;

  /**
   * Lo que se acordó devolver en esta cuota, siempre mayor que cero.
   */
  @Column("amount")
  private BigDecimal amount;
}
