package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.api.model.CategoryResponse;
import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.query.CategoryUsage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre la categoría y los modelos de categoría del contrato OpenAPI.
 *
 * El origen habitual es {@link CategoryUsage} y no la entidad, porque el número de
 * transacciones lo calcula la base de datos al listar y no vive en la tabla. La entidad
 * se convierte solo cuando la categoría viaja dentro de una transacción, donde ese conteo
 * no viene al caso y se informa a cero.
 *
 * Las correspondencias se declaran una a una a propósito: así, si alguna propiedad se
 * renombra, la compilación falla en lugar de dejar el campo silenciosamente a nulo.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

  /**
   * Convierte una categoría del catálogo en su representación de respuesta.
   *
   * @param categoryUsage categoría junto al número de transacciones que clasifica
   * @return la representación de la categoría
   */
  @Mapping(target = "id", source = "categoryId")
  @Mapping(target = "name", source = "categoryName")
  @Mapping(target = "appliesTo", source = "categoryScope")
  @Mapping(target = "isSystem", source = "systemCategory")
  @Mapping(target = "transactionCount", source = "transactionCount")
  CategoryResponse toResponse(CategoryUsage categoryUsage);

  /**
   * Convierte la entidad en la representación que viaja dentro de una transacción.
   * El conteo de transacciones se informa a cero: ahí la categoría identifica y clasifica,
   * y calcular cuántos movimientos más comparten esa categoría exigiría una consulta por
   * cada fila de la página sin que nadie la vaya a mirar.
   *
   * @param category entidad de la categoría
   * @return la representación de la categoría
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "appliesTo", source = "appliesTo")
  @Mapping(target = "isSystem", source = "system")
  @Mapping(target = "transactionCount", constant = "0L")
  CategoryResponse toResponse(Category category);

  /**
   * Traduce el ámbito guardado como texto al valor del contrato.
   *
   * @param appliesTo ámbito tal y como está en la base de datos
   * @return el valor equivalente del contrato, o el de egresos si no hubiera ninguno
   */
  default CategoryScope toScope(String appliesTo) {
    return appliesTo == null ? CategoryScope.EXPENSE : CategoryScope.fromValue(appliesTo);
  }
}
