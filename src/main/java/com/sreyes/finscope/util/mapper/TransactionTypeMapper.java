package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.api.model.TransactionTypeResponse;
import com.sreyes.finscope.model.entity.TransactionType;
import org.mapstruct.Mapper;

/**
 * Mapper entre la entidad {@link TransactionType} y los modelos de tipo de transacción
 * del contrato OpenAPI.
 */
@Mapper(componentModel = "spring")
public interface TransactionTypeMapper {

  /**
   * Convierte un tipo de transacción en su representación de respuesta.
   *
   * @param transactionType entidad de tipo de transacción
   * @return la representación del tipo de transacción
   */
  TransactionTypeResponse toResponse(TransactionType transactionType);
}
