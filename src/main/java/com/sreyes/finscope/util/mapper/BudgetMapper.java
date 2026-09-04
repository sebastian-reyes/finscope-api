package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.api.model.BudgetResponse;
import com.sreyes.finscope.model.query.BudgetProgress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre el presupuesto y el modelo de presupuesto del contrato OpenAPI.
 *
 * El origen es siempre {@link BudgetProgress} y nunca la entidad, porque lo gastado lo
 * calcula la base de datos al leer y no vive en la tabla. Un presupuesto sin su avance no
 * se responde en ningún sitio: por separado no dice nada.
 *
 * Las correspondencias se declaran una a una a propósito: así, si alguna propiedad se
 * renombra, la compilación falla en lugar de dejar el campo silenciosamente a nulo.
 */
@Mapper(componentModel = "spring")
public interface BudgetMapper {

  /**
   * Convierte un presupuesto con su avance en su representación de respuesta.
   *
   * <p>Lo que queda se calcula aquí y no en el cliente para que la resta se haga una sola
   * vez y con la misma escala decimal que los importes que la componen. Sale negativo
   * cuando el gasto ya se pasó del límite, que es justo el caso que hay que poder ver.</p>
   *
   * <p>Lo disponible descuenta además lo comprometido por los movimientos fijos que aún no
   * se han pagado. Son dos números y no uno porque responden a preguntas distintas: lo que
   * queda dice cómo va el mes, y lo disponible dice si se puede gastar algo más hoy.</p>
   *
   * @param budgetProgress presupuesto junto a lo gastado en su categoría durante su mes
   * @return la representación del presupuesto
   */
  @Mapping(target = "id", source = "budgetId")
  @Mapping(target = "categoryId", source = "budgetCategoryId")
  @Mapping(target = "category", source = "budgetCategoryName")
  @Mapping(target = "month", source = "budgetMonth")
  @Mapping(target = "year", source = "budgetYear")
  @Mapping(target = "amount", source = "budgetAmount")
  @Mapping(target = "spent", source = "budgetSpent")
  @Mapping(target = "committed", source = "budgetCommitted")
  @Mapping(target = "remaining",
      expression = "java(budgetProgress.budgetAmount().subtract(budgetProgress.budgetSpent()))")
  @Mapping(target = "available",
      expression = "java(budgetProgress.budgetAmount().subtract(budgetProgress.budgetSpent())"
          + ".subtract(budgetProgress.budgetCommitted()))")
  BudgetResponse toResponse(BudgetProgress budgetProgress);
}
