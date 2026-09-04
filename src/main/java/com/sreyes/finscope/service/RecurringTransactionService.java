package com.sreyes.finscope.service;

import com.sreyes.finscope.api.model.ConfirmRecurringTransactionRequest;
import com.sreyes.finscope.api.model.SaveRecurringTransactionRequest;
import com.sreyes.finscope.api.model.UpdateRecurringTransactionRequest;
import com.sreyes.finscope.model.entity.RecurringTransaction;
import com.sreyes.finscope.model.query.RecurringOccurrence;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio para la gestión de los movimientos fijos del usuario.
 * Toda operación parte del identificador del propietario, de modo que una plantilla ajena
 * se comporta igual que una inexistente.
 *
 * Hay dos formas de mirar un fijo y las dos hacen falta. La plantilla es lo que se edita y
 * no depende de ningún mes; la ocurrencia es esa misma plantilla resuelta contra uno, con
 * su día de vencimiento y su estado. Por eso el alta y la modificación devuelven la
 * plantilla —no están mirando ningún mes— y confirmar, omitir y listar devuelven
 * ocurrencias.
 *
 * El servicio no genera movimientos por su cuenta: la transacción solo aparece cuando el
 * usuario confirma un mes.
 */
public interface RecurringTransactionService {

  /**
   * Obtiene todos los movimientos fijos del usuario resueltos contra un mes.
   * Se devuelven también los pausados y los que no vencen ese mes, con estado NOT_DUE: la
   * pantalla de gestión necesita verlos siempre.
   *
   * @param userId identificador del usuario propietario
   * @param month  mes contra el que se resuelve el estado, entre 1 y 12
   * @param year   año contra el que se resuelve el estado
   * @return flujo reactivo con las plantillas del usuario y su estado en ese mes
   */
  Flux<RecurringOccurrence> findRecurring(Long userId, Integer month, Integer year);

  /**
   * Da de alta un movimiento fijo.
   * No crea ninguna transacción, ni siquiera la del mes en curso: el alta dice que ese
   * cargo se repite, no que ya haya ocurrido.
   *
   * @param userId  identificador del usuario propietario
   * @param request datos de la plantilla a crear
   * @return la plantilla creada
   */
  Mono<RecurringTransaction> createRecurring(Long userId,
                                             SaveRecurringTransactionRequest request);

  /**
   * Modifica un movimiento fijo. Solo se aplican los campos informados.
   * El cambio rige de aquí en adelante: los movimientos ya confirmados con esta plantilla
   * son hechos y no se recalculan.
   *
   * @param userId  identificador del usuario propietario
   * @param id      identificador de la plantilla
   * @param request datos a actualizar
   * @return la plantilla actualizada
   */
  Mono<RecurringTransaction> updateRecurring(Long userId, Long id,
                                             UpdateRecurringTransactionRequest request);

  /**
   * Elimina un movimiento fijo y sus omisiones.
   * Los movimientos que se confirmaron con él se quedan, porque ocurrieron; lo único que
   * pierden es el enlace.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la plantilla
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteRecurring(Long userId, Long id);

  /**
   * Registra el movimiento de un mes y lo deja enlazado a su plantilla.
   * Lo que no se indique se toma de la plantilla y del día previsto.
   *
   * @param userId  identificador del usuario propietario
   * @param id      identificador de la plantilla
   * @param request mes que se confirma y, si difiere de lo previsto, lo que de verdad pasó
   * @return la plantilla resuelta contra ese mes, ya como pagada
   */
  Mono<RecurringOccurrence> confirmRecurring(Long userId, Long id,
                                             ConfirmRecurringTransactionRequest request);

  /**
   * Marca que un mes no toca. No cambia la plantilla ni los demás meses.
   * Repetirlo no cambia nada.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la plantilla
   * @param month  mes que se omite
   * @param year   año de ese mes
   * @return la plantilla resuelta contra ese mes, ya como omitida
   */
  Mono<RecurringOccurrence> skipRecurring(Long userId, Long id, Integer month, Integer year);

  /**
   * Deshace la omisión de un mes. Si no estaba omitido no cambia nada, para que deshacer
   * siempre sea seguro.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la plantilla
   * @param month  mes cuya omisión se deshace
   * @param year   año de ese mes
   * @return la plantilla resuelta contra ese mes
   */
  Mono<RecurringOccurrence> unskipRecurring(Long userId, Long id, Integer month, Integer year);
}
