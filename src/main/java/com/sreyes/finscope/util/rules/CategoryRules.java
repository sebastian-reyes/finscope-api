package com.sreyes.finscope.util.rules;

import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.entity.TransactionType;
import lombok.experimental.UtilityClass;

/**
 * Reglas de uso de las categorías que necesita más de un servicio.
 * Esta clase no debe ser instanciada.
 *
 * Vive aparte porque la comprueban dos sitios: al registrar o corregir un movimiento y al
 * dar de alta o modificar un movimiento fijo. Son la misma pregunta —¿esta categoría puede
 * clasificar este tipo de movimiento?— y si cada uno tuviera su copia, un fijo podría
 * quedar con una pareja que después el registro rechazaría, y la confirmación fallaría el
 * día que tocara pagarlo.
 */
@UtilityClass
public final class CategoryRules {

  /**
   * Decide si una categoría puede clasificar un tipo de movimiento.
   * Una categoría sin ámbito declarado se admite en cualquiera: el campo solo existe para
   * afinar lo que propone el formulario, no para bloquear lo que el usuario ya eligió.
   *
   * @param category categoría elegida
   * @param type     tipo del movimiento
   * @return si la categoría admite ese tipo
   */
  public static boolean admits(Category category, TransactionType type) {
    String scope = category.getAppliesTo();
    return scope == null
        || CategoryScope.BOTH.getValue().equals(scope)
        || scope.equals(type.getCode());
  }
}
