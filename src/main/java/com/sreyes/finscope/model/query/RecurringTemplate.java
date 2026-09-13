package com.sreyes.finscope.model.query;

import com.sreyes.finscope.model.entity.RecurringTransaction;
import java.util.List;

/**
 * La plantilla de un movimiento fijo junto a sus tags, sin mirar ningún mes.
 *
 * Es lo que devuelven el alta y la modificación. Los tags no están en la entidad porque
 * viven en su propia tabla de enlace, y aun así tienen que volver en la respuesta: el
 * cliente acaba de escribirlos y necesita ver con qué grafía se guardaron, ya que un
 * `casa` sobre un `Casa` previo reutiliza el que ya existía en el catálogo.
 *
 * @param recurring plantilla tal y como quedó guardada
 * @param tags      tags de la plantilla, en orden alfabético; vacío si no lleva ninguno
 */
public record RecurringTemplate(RecurringTransaction recurring, List<String> tags) {
}
