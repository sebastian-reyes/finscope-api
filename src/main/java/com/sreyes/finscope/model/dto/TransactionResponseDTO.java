package com.sreyes.finscope.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionResponseDTO {
  private Long id;
  private BigDecimal amount;
  private String description;
  private LocalDateTime date;
  private CategoryDTO category;
  private TransactionTypeDTO transactionType;
}
