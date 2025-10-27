package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import com.sreyes.finscope.service.TransactionTypeService;
import com.sreyes.finscope.util.constants.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link TransactionTypeService} para la gestión de tipos de transacción.
 * Proporciona operaciones reactivas para consultar todos los tipos y buscar por identificador.
 * Utiliza {@link TransactionTypeRepository} para el acceso a datos.
 * Lanza {@link TransactionTypeNotFoundException} cuando no se encuentra un tipo de transacción.
 */
@Service
@RequiredArgsConstructor
public class TransactionTypeServiceImpl implements TransactionTypeService {

  private final TransactionTypeRepository transactionTypeRepository;

  @Override
  public Flux<TransactionType> findAllTransactionTypes() {
    return transactionTypeRepository.findAll();
  }

  @Override
  public Mono<TransactionType> findTransactionTypeById(Long id) {
    return transactionTypeRepository.findById(id)
        .switchIfEmpty(Mono
            .error(new TransactionTypeNotFoundException(
                Constants.TRANSACTION_TYPE_NOT_FOUND + id)));
  }
}
