package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.api.model.RecurringOccurrenceResponse;
import com.sreyes.finscope.api.model.RecurringTransactionResponse;
import com.sreyes.finscope.model.query.RecurringOccurrence;
import com.sreyes.finscope.model.query.RecurringTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre los movimientos fijos y los modelos del contrato OpenAPI.
 *
 * Hay dos conversiones porque hay dos formas de mirar un fijo. La plantilla sale de la
 * entidad y es lo que devuelven el alta y la modificación, que no están mirando ningún mes.
 * La ocurrencia sale de la proyección y lleva además el día de vencimiento y el estado, que
 * no viven en ninguna tabla y se resuelven al leer.
 *
 * Los tags entran en las dos por separado, nunca desde la entidad ni desde la proyección:
 * viven en su tabla de enlace y el servicio los carga en lote, igual que los de una página
 * de transacciones.
 */
@Mapper(componentModel = "spring")
public interface RecurringTransactionMapper {

  /**
   * Convierte la plantilla guardada, con sus tags, en su representación de respuesta.
   *
   * <p>Las correspondencias se declaran una a una porque la entidad viaja anidada dentro de
   * {@link RecurringTemplate}, junto a los tags. La plantilla no lleva el nombre de la
   * categoría ni el código del tipo porque son catálogos que el cliente ya tiene, y traerlos
   * obligaría a resolver dos uniones cada vez que se guarda.</p>
   *
   * @param template plantilla tal y como quedó guardada, junto a sus tags
   * @return la representación de la plantilla
   */
  @Mapping(target = "id", source = "recurring.id")
  @Mapping(target = "categoryId", source = "recurring.categoryId")
  @Mapping(target = "transactionTypeId", source = "recurring.transactionTypeId")
  @Mapping(target = "description", source = "recurring.description")
  @Mapping(target = "amount", source = "recurring.amount")
  @Mapping(target = "currency", source = "recurring.currency")
  @Mapping(target = "dayOfMonth", source = "recurring.dayOfMonth")
  @Mapping(target = "everyMonths", source = "recurring.everyMonths")
  @Mapping(target = "startMonth", source = "recurring.startMonth")
  @Mapping(target = "startYear", source = "recurring.startYear")
  @Mapping(target = "active", source = "recurring.active")
  @Mapping(target = "tags", source = "tags")
  RecurringTransactionResponse toResponse(RecurringTemplate template);

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
  @Mapping(target = "currency", source = "recurring.recurringCurrency")
  @Mapping(target = "dayOfMonth", source = "recurring.recurringDayOfMonth")
  @Mapping(target = "everyMonths", source = "recurring.recurringEveryMonths")
  @Mapping(target = "startMonth", source = "recurring.recurringStartMonth")
  @Mapping(target = "startYear", source = "recurring.recurringStartYear")
  @Mapping(target = "active", source = "recurring.recurringActive")
  @Mapping(target = "tags", source = "tags")
  @Mapping(target = "month", source = "recurring.recurringMonth")
  @Mapping(target = "year", source = "recurring.recurringYear")
  @Mapping(target = "dueDate", source = "dueDate")
  @Mapping(target = "status", source = "state")
  @Mapping(target = "transactionId", source = "recurring.recurringTransactionId")
  @Mapping(target = "paidAmount", source = "recurring.recurringPaidAmount")
  @Mapping(target = "paidDate", source = "recurring.recurringPaidDate")
  RecurringOccurrenceResponse toResponse(RecurringOccurrence occurrence);
}
