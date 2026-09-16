package com.sreyes.finscope.service.impl;

import org.springframework.transaction.reactive.TransactionCallback;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * Operador de transacciones para pruebas unitarias. No abre ninguna transacción: marca el
 * contexto de Reactor de lo que envuelve, de modo que un repositorio simulado puede decir si
 * se le llamó dentro o fuera de la transacción. Es lo que permite comprobar sin base de datos
 * que una escritura quedó dentro del bloque atómico y el correo, fuera.
 */
final class ContextTransactionalOperator implements TransactionalOperator {

  private static final String IN_TRANSACTION = ContextTransactionalOperator.class.getName();

  /**
   * Indica si un contexto pertenece a una cadena envuelta por este operador.
   *
   * @param context contexto de Reactor en el momento de la llamada
   * @return si la llamada ocurre dentro de la transacción
   */
  static boolean inTransaction(ContextView context) {
    return context.hasKey(IN_TRANSACTION);
  }

  @Override
  public <T> Flux<T> transactional(Flux<T> flux) {
    return flux.contextWrite(context -> context.put(IN_TRANSACTION, true));
  }

  @Override
  public <T> Mono<T> transactional(Mono<T> mono) {
    return mono.contextWrite(context -> context.put(IN_TRANSACTION, true));
  }

  @Override
  public <T> Flux<T> execute(TransactionCallback<T> action) {
    throw new UnsupportedOperationException("Only transactional(...) is supported in tests");
  }
}
