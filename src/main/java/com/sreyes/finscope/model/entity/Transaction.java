package com.sreyes.finscope.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa una transacción en el sistema.
 * Está mapeada a la tabla `transactions` en la base de datos.
 * El importe se guarda siempre en positivo; el signo lo aporta el tipo de transacción,
 * que indica si se trata de un ingreso o de un egreso. Y se guarda en la moneda en que
 * ocurrió el movimiento, sin convertirse: cien dólares son cien dólares el día que se
 * registran y el día que se consultan.
 * Lleva exactamente una categoría, que es lo que dice en qué se gastó y permite repartir
 * el total del periodo sin contar nada dos veces, y además cero o varios tags, que dicen
 * en qué contexto ocurrió y viven en la tabla de enlace `transaction_tags`.
 */
@Table("transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

  @Id
  @Column("id_transaction")
  private Long id;

  private BigDecimal amount;

  /**
   * Moneda del importe, en código ISO 4217.
   * Se guarda como texto y no como la enumeración del contrato para que la entidad no
   * dependa de los modelos generados: la traducción ocurre en el borde, al entrar y al
   * salir. Los valores admitidos los fija la restricción de la tabla.
   */
  private String currency;

  /**
   * Tipo de cambio a moneda base con el que se registró el movimiento, nulo cuando la
   * moneda ya es la base.
   *
   * No entra en ningún cálculo ni en ningún total: es memoria de aquel día. Guardarlo es
   * lo que permite responder cuánto fueron en soles estos cien dólares sin que la respuesta
   * cambie cada mañana, que es lo que pasaría calculándolo con el cambio de hoy.
   */
  @Column("exchange_rate")
  private BigDecimal exchangeRate;

  private String description;

  private LocalDateTime date;

  /**
   * Identificador del usuario propietario. Aisla los datos entre cuentas.
   */
  @Column("user_id")
  private Long userId;

  @Column("transaction_type_id")
  private Long transactionTypeId;

  /**
   * Categoría principal. Es obligatoria: sin ella el reparto del gasto por categoría
   * dejaría transacciones fuera y no sumaría el total del periodo.
   */
  @Column("category_id")
  private Long categoryId;

  /**
   * Movimiento fijo que originó esta transacción, nulo en las que se registraron a mano.
   * Es lo único que permite responder «ya pagué el alquiler de septiembre»: adivinarlo
   * comparando importe, categoría y descripción se rompe el primer mes que se paga de más
   * o se corrige el texto.
   *
   * Al borrar la transacción el enlace desaparece con ella y el fijo vuelve a estar
   * pendiente, que es exactamente lo que ha pasado.
   */
  @Column("recurring_id")
  private Long recurringId;

}
