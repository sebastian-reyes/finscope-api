package com.sreyes.finscope.controller;

import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.service.TransactionTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para gestionar los tipos de transacción.
 * Proporciona endpoints para consultar todos los tipos y obtener uno por su identificador.
 */
@RestController
@RequestMapping("/transaction-types")
@RequiredArgsConstructor
public class TransactionTypeController {

  private final TransactionTypeService transactionTypeService;

  /**
   * Obtiene todos los tipos de transacción registrados.
   *
   * @return un {@link Flux} de {@link TransactionType} con todos los tipos de transacción.
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public Flux<TransactionType> getAllTransactionTypes() {
    return transactionTypeService.findAllTransactionTypes();
  }

  /**
   * Obtiene un tipo de transacción por su identificador.
   *
   * @param id el identificador del tipo de transacción.
   * @return un {@link Mono} de {@link TransactionType} si existe, vacío en caso contrario.
   */
  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Mono<TransactionType> getTransactionTypeById(@PathVariable Long id) {
    return transactionTypeService.findTransactionTypeById(id);
  }

}
