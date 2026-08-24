package com.sreyes.finscope.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.api.model.TransactionResponse;
import com.sreyes.finscope.exception.custom.DateNotFoundException;
import com.sreyes.finscope.exception.custom.InvalidSortException;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.model.query.TransactionFilter;
import com.sreyes.finscope.model.query.TransactionSearchCriteria;
import com.sreyes.finscope.model.query.TransactionTagName;
import com.sreyes.finscope.repository.TagRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.repository.TransactionSearchRepository;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import com.sreyes.finscope.util.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link TransactionQueryServiceImpl}, centradas en la normalización de
 * los filtros, el ordenamiento, los metadatos de paginación y el acotado por usuario.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionQueryServiceImplTest {

  private static final Long USER_ID = 7L;

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private TransactionSearchRepository transactionSearchRepository;

  @Mock
  private TransactionTypeRepository transactionTypeRepository;

  @Mock
  private TagRepository tagRepository;

  @Mock
  private TransactionMapper transactionMapper;

  @InjectMocks
  private TransactionQueryServiceImpl transactionQueryService;

  /**
   * Configura una búsqueda que devuelve una única transacción sin tags.
   *
   * @param totalElements total de transacciones que cumplen los filtros
   */
  private void givenSearchReturnsOneTransaction(long totalElements) {
    Transaction transaction = new Transaction(1L, new BigDecimal("300.00"), "Videojuego",
        LocalDateTime.of(2026, 8, 17, 20, 0), USER_ID, 3L);
    when(transactionSearchRepository.search(any(), any())).thenReturn(Flux.just(transaction));
    when(transactionSearchRepository.count(any())).thenReturn(Mono.just(totalElements));
    when(transactionTypeRepository.findAllById(any(Iterable.class)))
        .thenReturn(Flux.just(new TransactionType(3L, "Egreso", "EXPENSE")));
    when(tagRepository.findNamesByTransactionIdIn(any())).thenReturn(Flux.empty());
    when(transactionMapper.toResponse(any(), any(), any()))
        .thenReturn(new TransactionResponse());
  }

  /**
   * Construye unos criterios de búsqueda con la paginación indicada y sin filtros.
   *
   * @param page página solicitada
   * @param size tamaño de página solicitado
   * @param sort criterio de ordenamiento
   * @return los criterios de búsqueda
   */
  private TransactionSearchCriteria criteria(int page, int size, String sort) {
    return new TransactionSearchCriteria(null, null, null, null, null, null, page, size, sort);
  }

  @Test
  @DisplayName("Calcula los metadatos de paginación a partir del total de coincidencias")
  void buildsPaginationMetadata() {
    givenSearchReturnsOneTransaction(11L);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria(1, 2, null)))
        .assertNext(page -> {
          assertEquals(1, page.getPage());
          assertEquals(2, page.getSize());
          assertEquals(11L, page.getTotalElements());
          assertEquals(6, page.getTotalPages());
          assertEquals(1, page.getContent().size());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Ordena por fecha descendente cuando no se indica criterio")
  void appliesDefaultSort() {
    givenSearchReturnsOneTransaction(1L);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria(0, 20, null)))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(transactionSearchRepository).search(any(), captor.capture());
    assertEquals(Sort.by(Sort.Direction.DESC, "date"), captor.getValue().getSort());
  }

  @Test
  @DisplayName("Interpreta el criterio de ordenamiento indicado")
  void appliesRequestedSort() {
    givenSearchReturnsOneTransaction(1L);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID,
        criteria(0, 20, "amount,asc")))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(transactionSearchRepository).search(any(), captor.capture());
    assertEquals(Sort.by(Sort.Direction.ASC, "amount"), captor.getValue().getSort());
  }

  @Test
  @DisplayName("Rechaza ordenar por un campo que no está en la lista blanca")
  void rejectsUnknownSortField() {
    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID,
        criteria(0, 20, "name_tag,asc")))
        .expectError(InvalidSortException.class)
        .verify();

    verify(transactionSearchRepository, never()).search(any(), any());
  }

  @Test
  @DisplayName("Rechaza una dirección de ordenamiento desconocida")
  void rejectsUnknownSortDirection() {
    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID,
        criteria(0, 20, "date,upwards")))
        .expectError(InvalidSortException.class)
        .verify();
  }

  @Test
  @DisplayName("Convierte el filtro de mes y año en el rango del mes completo")
  void translatesMonthFilterIntoDateRange() {
    givenSearchReturnsOneTransaction(1L);
    TransactionSearchCriteria criteria = new TransactionSearchCriteria(8, 2026, null, null,
        null, null, 0, 20, null);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<TransactionFilter> captor = ArgumentCaptor.forClass(TransactionFilter.class);
    verify(transactionSearchRepository).search(captor.capture(), any());
    assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), captor.getValue().dateFrom());
    assertEquals(2026, captor.getValue().dateTo().getYear());
    assertEquals(31, captor.getValue().dateTo().getDayOfMonth());
  }

  @Test
  @DisplayName("Rechaza combinar el filtro de mes con un rango explícito de fechas")
  void rejectsConflictingDateFilters() {
    TransactionSearchCriteria criteria = new TransactionSearchCriteria(8, 2026,
        LocalDateTime.of(2026, 1, 1, 0, 0), null, null, null, 0, 20, null);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria))
        .expectError(DateNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Exige informar mes y año conjuntamente")
  void rejectsIncompleteMonthFilter() {
    TransactionSearchCriteria criteria = new TransactionSearchCriteria(8, null, null, null,
        null, null, 0, 20, null);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria))
        .expectError(DateNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza un rango de fechas invertido")
  void rejectsInvertedDateRange() {
    TransactionSearchCriteria criteria = new TransactionSearchCriteria(null, null,
        LocalDateTime.of(2026, 8, 31, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0),
        null, null, 0, 20, null);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria))
        .expectError(DateNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Restringe la búsqueda a las transacciones que llevan el tag indicado")
  void restrictsSearchToTaggedTransactions() {
    givenSearchReturnsOneTransaction(1L);
    when(tagRepository.findTransactionIdsByUserIdAndName(USER_ID, "ocio"))
        .thenReturn(Flux.just(1L, 2L));
    TransactionSearchCriteria criteria = new TransactionSearchCriteria(null, null, null, null,
        null, "ocio", 0, 20, null);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<TransactionFilter> captor = ArgumentCaptor.forClass(TransactionFilter.class);
    verify(transactionSearchRepository).search(captor.capture(), any());
    assertEquals(List.of(1L, 2L), captor.getValue().transactionIds());
  }

  @Test
  @DisplayName("Devuelve una página vacía cuando ninguna transacción lleva el tag indicado")
  void returnsEmptyPageWhenTagHasNoTransactions() {
    when(tagRepository.findTransactionIdsByUserIdAndName(USER_ID, "inexistente"))
        .thenReturn(Flux.empty());
    TransactionSearchCriteria criteria = new TransactionSearchCriteria(null, null, null, null,
        null, "inexistente", 0, 20, null);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria))
        .assertNext(page -> {
          assertEquals(0, page.getContent().size());
          assertEquals(0L, page.getTotalElements());
          assertEquals(0, page.getTotalPages());
        })
        .verifyComplete();

    verify(transactionSearchRepository, never()).search(any(), any());
  }

  @Test
  @DisplayName("Entrega los tags de la transacción ordenados alfabéticamente")
  void sortsTagsAlphabetically() {
    givenSearchReturnsOneTransaction(1L);
    when(tagRepository.findNamesByTransactionIdIn(any()))
        .thenReturn(Flux.just(new TransactionTagName(1L, "personal"),
            new TransactionTagName(1L, "Ocio")));

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria(0, 20, null)))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(transactionMapper).toResponse(any(), any(), captor.capture());
    assertEquals(List.of("Ocio", "personal"), captor.getValue());
  }

  @Test
  @DisplayName("Entrega una lista vacía cuando la transacción no tiene tags")
  void returnsEmptyTagsWhenTransactionHasNone() {
    givenSearchReturnsOneTransaction(1L);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria(0, 20, null)))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(transactionMapper).toResponse(any(), any(), captor.capture());
    assertEquals(List.of(), captor.getValue());
  }

  @Test
  @DisplayName("Resuelve el tipo de la transacción antes de ensamblar la respuesta")
  void resolvesTransactionType() {
    givenSearchReturnsOneTransaction(1L);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria(0, 20, null)))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<TransactionType> captor = ArgumentCaptor.forClass(TransactionType.class);
    verify(transactionMapper).toResponse(any(), captor.capture(), any());
    assertEquals("EXPENSE", captor.getValue().getCode());
  }

  @Test
  @DisplayName("Falla al consultar una transacción inexistente")
  void failsOnMissingTransaction() {
    when(transactionRepository.findByIdAndUserId(anyLong(), anyLong())).thenReturn(Mono.empty());

    StepVerifier.create(transactionQueryService.getTransactionById(USER_ID, 99L))
        .expectError(TransactionNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Acota la búsqueda al usuario que la pide")
  void restrictsSearchToRequestingUser() {
    givenSearchReturnsOneTransaction(1L);

    StepVerifier.create(transactionQueryService.searchTransactions(USER_ID, criteria(0, 20, null)))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<TransactionFilter> captor = ArgumentCaptor.forClass(TransactionFilter.class);
    verify(transactionSearchRepository).search(captor.capture(), any());
    assertEquals(USER_ID, captor.getValue().userId());
  }

  @Test
  @DisplayName("Busca los tags de otro usuario acotados a ese usuario")
  void scopesTagLookupToRequestingUser() {
    givenSearchReturnsOneTransaction(1L);
    when(tagRepository.findTransactionIdsByUserIdAndName(eq(8L), any())).thenReturn(Flux.empty());
    TransactionSearchCriteria criteria = new TransactionSearchCriteria(null, null, null, null,
        null, "ocio", 0, 20, null);

    StepVerifier.create(transactionQueryService.searchTransactions(8L, criteria))
        .expectNextCount(1)
        .verifyComplete();

    verify(tagRepository).findTransactionIdsByUserIdAndName(8L, "ocio");
    verify(transactionSearchRepository, never()).search(any(), any());
  }

  @Test
  @DisplayName("No deja ver la transacción de otro usuario")
  void hidesTransactionFromAnotherUser() {
    when(transactionRepository.findByIdAndUserId(1L, 8L)).thenReturn(Mono.empty());

    StepVerifier.create(transactionQueryService.getTransactionById(8L, 1L))
        .expectError(TransactionNotFoundException.class)
        .verify();
  }
}
