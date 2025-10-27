package com.sreyes.finscope.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Objeto de transferencia de datos (DTO) para el tipo de transacción.
 * Contiene el identificador y el nombre del tipo de transacción.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionTypeDto {
  private Long id;
  private String name;
}
