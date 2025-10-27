package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.DateNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.dto.TransactionResponseDto;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import com.sreyes.finscope.service.TransactionQueryService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.mapper.MapperUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link TransactionQueryService} para la consulta de transacciones.
 * Proporciona operaciones reactivas para obtener transacciones filtradas por tipo, categoría, fecha o identificador.
 * Utiliza {@link TransactionRepository}, {@link CategoryRepository} y {@link TransactionTypeRepository} para el acceso a datos.
 * Lanza excepciones personalizadas como {@link TransactionNotFoundException}, {@link CategoryNotFoundException},
 * {@link TransactionTypeNotFoundException} y {@link DateNotFoundException} según corresponda.
 */
@Service
@RequiredArgsConstructor
public class TransactionQueryServiceImpl implements TransactionQueryService {

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final MapperUtil mapperUtil;

  @Override
  public Flux<TransactionResponseDto> getAllTransactions() {
    return transactionRepository.findAll()
        .flatMap(transaction -> categoryRepository.findById(transaction.getCategoryId())
            .flatMap(category -> transactionTypeRepository
                .findById(transaction.getTransactionTypeId())
                .map(transactionType -> mapperUtil
                    .buildTransactionResponseDto(transaction, category, transactionType))
            )
        );
  }

  @Override
  public Flux<TransactionResponseDto> getAllTransactionsByTypeId(Long id) {
    return transactionRepository.findByTransactionTypeId(id)
        .flatMap(transaction -> categoryRepository.findById(transaction.getCategoryId())
            .switchIfEmpty(Mono.error(new CategoryNotFoundException(
                Constants.CATEGORY_NOT_FOUND + transaction.getCategoryId())))
            .flatMap(category -> transactionTypeRepository.findById(transaction
                    .getTransactionTypeId())
                .switchIfEmpty(Mono.error(new TransactionTypeNotFoundException(
                    Constants.TRANSACTION_TYPE_NOT_FOUND + transaction.getTransactionTypeId())))
                .map(transactionType -> mapperUtil.buildTransactionResponseDto(transaction,
                    category, transactionType))
            )
        );
  }

  @Override
  public Flux<TransactionResponseDto> getAllTransactionsByCategoryId(Long id) {
    return transactionRepository.findByCategoryId(id)
        .flatMap(transaction -> categoryRepository.findById(transaction.getCategoryId())
            .switchIfEmpty(Mono.error(new CategoryNotFoundException(
                Constants.CATEGORY_NOT_FOUND + transaction.getCategoryId())))
            .flatMap(category -> transactionTypeRepository.findById(transaction
                    .getTransactionTypeId())
                .switchIfEmpty(Mono.error(new TransactionTypeNotFoundException(
                    Constants.TRANSACTION_TYPE_NOT_FOUND + transaction.getTransactionTypeId())))
                .map(transactionType -> mapperUtil.buildTransactionResponseDto(transaction,
                    category, transactionType))
            )
        );
  }

  @Override
  public Mono<TransactionResponseDto> getTransactionById(Long id) {
    return transactionRepository.findById(id)
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)))
        .flatMap(transaction -> categoryRepository.findById(transaction.getCategoryId())
            .flatMap(category -> transactionTypeRepository
                .findById(transaction.getTransactionTypeId())
                .map(transactionType -> mapperUtil
                    .buildTransactionResponseDto(transaction, category, transactionType))
            )
        );
  }

  @Override
  public Flux<TransactionResponseDto> getTransactionsByMonthAndYear(Integer month, Integer year) {
    return Mono.just(month)
        .filter(m -> m >= 1 && m <= 12)
        .switchIfEmpty(Mono.error(new DateNotFoundException(Constants.INVALID_MONTH)))
        .flatMapMany(validMonth -> transactionRepository.findByMonthAndYear(year, validMonth)
            .flatMap(transaction -> categoryRepository.findById(transaction.getCategoryId())
                .switchIfEmpty(Mono.error(new CategoryNotFoundException(
                    Constants.CATEGORY_NOT_FOUND + transaction.getCategoryId())))
                .flatMap(category -> transactionTypeRepository.findById(transaction
                        .getTransactionTypeId())
                    .switchIfEmpty(Mono.error(new TransactionTypeNotFoundException(
                        Constants.TRANSACTION_TYPE_NOT_FOUND + transaction.getTransactionTypeId())))
                    .map(transactionType -> mapperUtil.buildTransactionResponseDto(transaction,
                        category, transactionType))
                )
            )
        );
  }
}
