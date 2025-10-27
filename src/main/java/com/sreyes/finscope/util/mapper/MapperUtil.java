package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.model.dto.CategoryDto;
import com.sreyes.finscope.model.dto.TransactionResponseDto;
import com.sreyes.finscope.model.dto.TransactionTypeDto;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionType;
import org.springframework.stereotype.Component;

/**
 * Utilidad para mapear entidades a sus respectivos DTO en la aplicación FinScope. <br/>
 * Proporciona métodos para construir objetos DTO a partir de entidades del dominio.
 */
@Component
public class MapperUtil {

  /**
   * Construye un objeto {@link CategoryDto} a partir de una entidad {@link Category}.
   * @param category la entidad de categoría a mapear
   * @return el DTO de categoría construido
   */
  public CategoryDto buildCategoryDto(Category category) {
    return CategoryDto.builder()
        .id(category.getId())
        .name(category.getName())
        .build();
  }

  /**
   * Construye un objeto {@link TransactionTypeDto} a partir de una entidad {@link TransactionType}.
   * @param transactionType la entidad de tipo de transacción a mapear
   * @return el DTO de tipo de transacción construido
   */
  public TransactionTypeDto buildTransactionTypeDto(TransactionType transactionType) {
    return TransactionTypeDto.builder()
        .id(transactionType.getId())
        .name(transactionType.getName())
        .build();
  }

  /**
   * Construye un objeto {@link TransactionResponseDto} a partir de una entidad {@link Transaction},
   * una entidad {@link Category} y una entidad {@link TransactionType}.
   * @param transaction la entidad de transacción a mapear
   * @param category la entidad de categoría asociada
   * @param transactionType la entidad de tipo de transacción asociada
   * @return el DTO de respuesta de transacción construido
   */
  public TransactionResponseDto buildTransactionResponseDto(Transaction transaction,
                                                            Category category,
                                                            TransactionType transactionType) {
    return TransactionResponseDto.builder()
        .id(transaction.getId())
        .amount(transaction.getAmount())
        .description(transaction.getDescription())
        .date(transaction.getDate())
        .category(buildCategoryDto(category))
        .transactionType(buildTransactionTypeDto(transactionType))
        .build();
  }
}
