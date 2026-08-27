package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.api.model.TransactionResponse;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionType;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper que ensambla la respuesta de una transacción a partir de la transacción, de su
 * tipo, de su categoría y de sus tags.
 * La categoría viaja entera, con su identificador, porque el cliente la necesita para
 * preseleccionarla al editar y para filtrar por ella; los tags viajan como texto, que es
 * todo lo que hace falta para mostrarlos y para volver a guardarlos.
 */
@Mapper(componentModel = "spring",
    uses = {TransactionTypeMapper.class, CategoryMapper.class})
public interface TransactionMapper {

  /**
   * Ensambla la representación completa de una transacción.
   *
   * @param transaction     entidad de transacción
   * @param transactionType tipo de la transacción
   * @param category        categoría principal de la transacción
   * @param tags            tags asociados a la transacción
   * @return la representación completa de la transacción
   */
  @Mapping(target = "id", source = "transaction.id")
  @Mapping(target = "amount", source = "transaction.amount")
  @Mapping(target = "description", source = "transaction.description")
  @Mapping(target = "date", source = "transaction.date")
  @Mapping(target = "transactionType", source = "transactionType")
  @Mapping(target = "category", source = "category")
  @Mapping(target = "tags", source = "tags")
  TransactionResponse toResponse(Transaction transaction, TransactionType transactionType,
                                 Category category, List<String> tags);
}
