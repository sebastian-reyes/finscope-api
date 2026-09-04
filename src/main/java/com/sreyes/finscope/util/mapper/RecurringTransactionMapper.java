package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.api.model.RecurringOccurrenceResponse;
import com.sreyes.finscope.api.model.RecurringTransactionResponse;
import com.sreyes.finscope.model.entity.RecurringTransaction;
import com.sreyes.finscope.model.query.RecurringOccurrence;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre los movimientos fijos y los modelos del contrato OpenAPI.
 *
 * Hay dos conversiones porque hay dos formas de mirar un fijo. La plantilla sale de la
 * entidad y es lo que devuelven el alta y la modificación, que no están mirando ningún mes.
 * La ocurrencia sale de la proyección y lleva además el día de vencimiento y el estado, que
 * no viven en ninguna tabla y se resuelven al leer.
 */
@Mapper(componentModel = "spring")
public interface RecurringTransactionMapper {

  /**
   * Convierte la plantilla guardada en su representación de respuesta.
   *
   * <p>Aquí no hacen falta correspondencias explícitas: las propiedades se llaman igual a
   * ambos lados. La plantilla no lleva el nombre de la categoría ni el código del tipo
   * porque son catálogos que el cliente ya tiene, y traerlos obligaría a resolver dos
   * uniones cada vez que se guarda.</p>
   *
   * @param recurring plantilla tal y como quedó guardada
   * @return la representación de la plantilla
   */
  RecurringTransactionResponse toResponse(RecurringTransaction recurring);

  /**
   * Convierte un movimiento fijo resuelto contra un mes en su representación de respuesta.
   *
   * <p>Las correspondencias se declaran una a una a propósito: los nombres de la proyección
   * llevan prefijo para no chocar con los de la entidad, así que si alguno se renombra la
   * compilación falla en lugar de dejar el campo silenciosamente a nulo.</p>
   *
   * @param occurrence plantilla resuelta contra un mes, con su vencimiento y su estado
   * @return la representación de la ocurrencia
   */
  @Mapping(target = "id", source = "recurring.recurringId")
  @Mapping(target = "categoryId", source = "recurring.recurringCategoryId")
  @Mapping(target = "category", source = "recurring.recurringCategoryName")
  @Mapping(target = "transactionTypeId", source = "recurring.recurringTypeId")
  @Mapping(target = "type", source = "recurring.recurringTypeCode")
  @Mapping(target = "description", source = "recurring.recurringDescription")
  @Mapping(target = "amount", source = "recurring.recurringAmount")
  @Mapping(target = "dayOfMonth", source = "recurring.recurringDayOfMonth")
  @Mapping(target = "everyMonths", source = "recurring.recurringEveryMonths")
  @Mapping(target = "startMonth", source = "recurring.recurringStartMonth")
  @Mapping(target = "startYear", source = "recurring.recurringStartYear")
  @Mapping(target = "active", source = "recurring.recurringActive")
  @Mapping(target = "month", source = "recurring.recurringMonth")
  @Mapping(target = "year", source = "recurring.recurringYear")
  @Mapping(target = "dueDate", source = "dueDate")
  @Mapping(target = "status", source = "state")
  @Mapping(target = "transactionId", source = "recurring.recurringTransactionId")
  @Mapping(target = "paidAmount", source = "recurring.recurringPaidAmount")
  @Mapping(target = "paidDate", source = "recurring.recurringPaidDate")
  RecurringOccurrenceResponse toResponse(RecurringOccurrence occurrence);
}
