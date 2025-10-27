package com.sreyes.finscope.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Objeto de transferencia de datos (DTO) para la respuesta de una transacción.
 * Contiene información relevante de la transacción, como el identificador, monto, descripción,
 * fecha, categoría y tipo de transacción.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionResponseDto {
  private Long id;
  private BigDecimal amount;
  private String description;
  private LocalDateTime date;
  private CategoryDto category;
  private TransactionTypeDto transactionType;
}
