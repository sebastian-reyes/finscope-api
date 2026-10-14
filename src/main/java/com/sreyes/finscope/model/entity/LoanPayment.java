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
 * Entidad que representa dinero que el deudor devolvió de un {@link Loan}.
 * Está mapeada a la tabla `loan_payments` en la base de datos.
 *
 * No se ata a ninguna {@link LoanInstallment}: se aplica en orden a la más antigua que siga
 * pendiente. Borrar un pago devuelve solas a pendiente las cuotas que cubría.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("loan_payments")
public class LoanPayment {

  @Id
  @Column("id_loan_payment")
  private Long id;

  @Column("loan_id")
  private Long loanId;

  @Column("paid_on")
  private LocalDate paidOn;

  /**
   * Lo devuelto, siempre mayor que cero.
   */
  @Column("amount")
  private BigDecimal amount;

  @Column("note")
  private String note;
}
