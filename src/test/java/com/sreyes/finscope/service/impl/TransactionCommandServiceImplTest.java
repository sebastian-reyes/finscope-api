package com.sreyes.finscope.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.api.model.CreateTransactionRequest;
import com.sreyes.finscope.api.model.UpdateTransactionRequest;
import com.sreyes.finscope.exception.custom.CategoryNotApplicableException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionTag;
import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.repository.TagRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.repository.TransactionTagRepository;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link TransactionCommandServiceImpl}, centradas en la validación del
 * tipo de transacción, en la normalización y el reemplazo de los tags, y en que la escritura
 * quede acotada al usuario que la pide.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionCommandServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final Long OTHER_USER_ID = 8L;

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private TransactionTypeRepository transactionTypeRepository;

  @Mock
  private CategoryRepository categoryRepository;

  @Mock
  private TagRepository tagRepository;

  @Mock
  private TransactionTagRepository transactionTagRepository;

  /** Reloj real: la creación sin fecha usa la hora actual y un mock devolvería null. */
  @Spy
  private Clock clock = Clock.systemDefaultZone();

  @InjectMocks
  private TransactionCommandServiceImpl transactionCommandService;

  /**
   * Configura el tipo y la categoría como existentes y la persistencia como satisfactoria.
   */
  private void givenValidReferences() {
    when(transactionTypeRepository.findById(2L))
        .thenReturn(Mono.just(new TransactionType(2L, "Egreso", "EXPENSE")));
    when(categoryRepository.findByIdAndUserId(3L, USER_ID))
        .thenReturn(Mono.just(new Category(3L, USER_ID, "Entretenimiento", "EXPENSE", false)));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> {
          Transaction transaction = invocation.getArgument(0);
          transaction.setId(10L);
          return Mono.just(transaction);
        });
    when(transactionTagRepository.deleteByTransactionId(anyLong())).thenReturn(Mono.empty());
    when(transactionTagRepository.saveAll(any(Iterable.class))).thenReturn(Flux.empty());
    when(tagRepository.insertIfAbsent(anyLong(), anyString())).thenReturn(Mono.just(1L));
    givenCatalogResolvesRequestedNames();
  }

  /**
   * Simula el catálogo devolviendo un tag por cada nombre solicitado, con un identificador
   * derivado de su posición. Basta para comprobar que los enlaces se crean con lo que el
   * catálogo resolvió, sin depender de identificadores concretos.
   */
  private void givenCatalogResolvesRequestedNames() {
    when(tagRepository.findByUserIdAndLowerNameIn(anyLong(), any()))
        .thenAnswer(invocation -> {
          Long userId = invocation.getArgument(0);
          Collection<String> lowerNames = invocation.getArgument(1);
          if (lowerNames == null) {
            return Flux.empty();
          }
          List<Tag> tags = new java.util.ArrayList<>();
          long id = 100L;
          for (String lowerName : lowerNames) {
            tags.add(new Tag(id++, userId, lowerName));
          }
          return Flux.fromIterable(tags);
        });
  }

  /**
   * Captura los tags que se han persistido para la transacción.
   *
   * @return los nombres de tag guardados, en el orden en que se guardaron
   */
  private List<String> capturedTagNames() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(tagRepository, atLeast(0)).insertIfAbsent(eq(USER_ID), captor.capture());
    return captor.getAllValues();
  }

  /**
   * Captura los identificadores de tag con los que se enlazó la transacción.
   *
   * @return los identificadores enlazados, en el orden en que se guardaron
   */
  private List<Long> capturedLinkedTagIds() {
    ArgumentCaptor<Iterable<TransactionTag>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(transactionTagRepository).saveAll(captor.capture());
    List<Long> tagIds = new java.util.ArrayList<>();
    captor.getValue().forEach(link -> tagIds.add(link.getTagId()));
    return tagIds;
  }

  /**
   * Construye una petición de creación válida con los tags indicados.
   *
   * @param tags nombres de los tags a asociar
   * @return la petición de creación
   */
  private CreateTransactionRequest createRequest(List<String> tags) {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setAmount(new BigDecimal("300.00"));
    request.setDescription("Videojuego");
    request.setTransactionTypeId(2L);
    request.setCategoryId(3L);
    request.setTags(tags);
    return request;
  }

  @Test
  @DisplayName("Crea una transacción con su tipo y sus tags")
  void createsTransactionWithTags() {
    givenValidReferences();

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID,
        createRequest(List.of("ocio", "personal"))))
        .assertNext(saved -> {
          assertEquals(new BigDecimal("300.00"), saved.getAmount());
          assertEquals(2L, saved.getTransactionTypeId());
        })
        .verifyComplete();

    assertEquals(List.of("ocio", "personal"), capturedTagNames());
  }

  @Test
  @DisplayName("Usa la fecha actual cuando la petición no informa ninguna")
  void defaultsDateToNow() {
    givenValidReferences();

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID, createRequest(null)))
        .assertNext(saved -> assertNotNull(saved.getDate()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Respeta la fecha informada en la petición")
  void keepsProvidedDate() {
    givenValidReferences();
    LocalDateTime date = LocalDateTime.of(2026, 4, 26, 13, 35);
    CreateTransactionRequest request = createRequest(null);
    request.setDate(date);

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID, request))
        .assertNext(saved -> assertEquals(date, saved.getDate()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Recorta los espacios y descarta los tags vacíos")
  void trimsAndDiscardsBlankTags() {
    givenValidReferences();

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID,
        createRequest(List.of("  ocio  ", "   ", "personal"))))
        .expectNextCount(1)
        .verifyComplete();

    assertEquals(List.of("ocio", "personal"), capturedTagNames());
  }

  @Test
  @DisplayName("Elimina los tags repetidos sin distinguir mayúsculas")
  void deduplicatesTagsIgnoringCase() {
    givenValidReferences();

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID,
        createRequest(List.of("Ocio", "ocio", "OCIO"))))
        .expectNextCount(1)
        .verifyComplete();

    assertEquals(List.of("Ocio"), capturedTagNames());
  }

  @Test
  @DisplayName("Rechaza la creación cuando el tipo de transacción no existe")
  void rejectsMissingTransactionType() {
    givenValidReferences();
    when(transactionTypeRepository.findById(2L)).thenReturn(Mono.empty());

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID, createRequest(null)))
        .expectError(TransactionTypeNotFoundException.class)
        .verify();

    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  @DisplayName("Guarda la transacción con la categoría elegida")
  void createsTransactionWithCategory() {
    givenValidReferences();

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID, createRequest(null)))
        .assertNext(saved -> assertEquals(3L, saved.getCategoryId()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Rechaza la creación cuando la categoría no existe o es de otro usuario")
  void rejectsMissingCategory() {
    givenValidReferences();
    when(categoryRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID, createRequest(null)))
        .expectError(CategoryNotFoundException.class)
        .verify();

    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  @DisplayName("Rechaza clasificar un egreso con una categoría de ingresos")
  void rejectsCategoryThatDoesNotAdmitTheType() {
    givenValidReferences();
    when(categoryRepository.findByIdAndUserId(3L, USER_ID))
        .thenReturn(Mono.just(new Category(3L, USER_ID, "Salario", "INCOME", false)));

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID, createRequest(null)))
        .expectError(CategoryNotApplicableException.class)
        .verify();

    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  @DisplayName("Admite una categoría de ámbito mixto en cualquier tipo")
  void acceptsCategoryThatAppliesToBoth() {
    givenValidReferences();
    when(categoryRepository.findByIdAndUserId(3L, USER_ID))
        .thenReturn(Mono.just(new Category(3L, USER_ID, "Otros", "BOTH", true)));

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID, createRequest(null)))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  @DisplayName("Cambia la categoría de la transacción al actualizarla")
  void updatesCategory() {
    givenValidReferences();
    when(transactionRepository.findByIdAndUserId(10L, USER_ID))
        .thenReturn(Mono.just(existingTransaction()));
    when(categoryRepository.findByIdAndUserId(9L, USER_ID))
        .thenReturn(Mono.just(new Category(9L, USER_ID, "Comida", "EXPENSE", false)));
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setCategoryId(9L);

    StepVerifier.create(transactionCommandService.updateTransaction(USER_ID, 10L, request))
        .assertNext(updated -> assertEquals(9L, updated.getCategoryId()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Revalida la pareja al cambiar solo el tipo de la transacción")
  void revalidatesCategoryWhenOnlyTypeChanges() {
    givenValidReferences();
    when(transactionRepository.findByIdAndUserId(10L, USER_ID))
        .thenReturn(Mono.just(existingTransaction()));
    when(transactionTypeRepository.findById(1L))
        .thenReturn(Mono.just(new TransactionType(1L, "Ingreso", "INCOME")));
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setTransactionTypeId(1L);

    // La categoría guardada es de egresos y la transacción pasa a ser un ingreso.
    StepVerifier.create(transactionCommandService.updateTransaction(USER_ID, 10L, request))
        .expectError(CategoryNotApplicableException.class)
        .verify();

    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  @DisplayName("Reemplaza los tags cuando la actualización los informa")
  void replacesTagsWhenProvided() {
    givenValidReferences();
    when(transactionRepository.findByIdAndUserId(10L, USER_ID))
        .thenReturn(Mono.just(existingTransaction()));
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setTags(List.of("digital"));

    StepVerifier.create(transactionCommandService.updateTransaction(USER_ID, 10L, request))
        .expectNextCount(1)
        .verifyComplete();

    verify(transactionTagRepository).deleteByTransactionId(10L);
    assertEquals(List.of("digital"), capturedTagNames());
  }

  @Test
  @DisplayName("Conserva los tags actuales cuando la actualización no los informa")
  void keepsTagsWhenNotProvided() {
    givenValidReferences();
    when(transactionRepository.findByIdAndUserId(10L, USER_ID))
        .thenReturn(Mono.just(existingTransaction()));
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setDescription("Descripción editada");

    StepVerifier.create(transactionCommandService.updateTransaction(USER_ID, 10L, request))
        .assertNext(updated -> assertEquals("Descripción editada", updated.getDescription()))
        .verifyComplete();

    verify(transactionTagRepository, never()).deleteByTransactionId(anyLong());
  }

  @Test
  @DisplayName("Elimina todos los tags cuando la actualización informa una lista vacía")
  void clearsTagsWhenEmptyListProvided() {
    givenValidReferences();
    when(transactionRepository.findByIdAndUserId(10L, USER_ID))
        .thenReturn(Mono.just(existingTransaction()));
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setTags(List.of());

    StepVerifier.create(transactionCommandService.updateTransaction(USER_ID, 10L, request))
        .expectNextCount(1)
        .verifyComplete();

    verify(transactionTagRepository).deleteByTransactionId(10L);
    assertEquals(List.of(), capturedTagNames());
  }

  @Test
  @DisplayName("Conserva los valores actuales de los campos no informados")
  void keepsUntouchedFieldsOnPartialUpdate() {
    givenValidReferences();
    when(transactionRepository.findByIdAndUserId(10L, USER_ID))
        .thenReturn(Mono.just(existingTransaction()));
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setAmount(new BigDecimal("55.00"));

    StepVerifier.create(transactionCommandService.updateTransaction(USER_ID, 10L, request))
        .assertNext(updated -> {
          assertEquals(new BigDecimal("55.00"), updated.getAmount());
          assertEquals("Original", updated.getDescription());
          assertEquals(2L, updated.getTransactionTypeId());
          assertEquals(3L, updated.getCategoryId());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Falla al actualizar una transacción inexistente")
  void failsUpdatingMissingTransaction() {
    when(transactionRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(transactionCommandService.updateTransaction(USER_ID, 99L,
        new UpdateTransactionRequest()))
        .expectError(TransactionNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Elimina físicamente la transacción existente")
  void deletesExistingTransaction() {
    Transaction transaction = existingTransaction();
    when(transactionRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Mono.just(transaction));
    when(transactionRepository.delete(transaction)).thenReturn(Mono.empty());

    StepVerifier.create(transactionCommandService.deleteTransactionById(USER_ID, 10L))
        .verifyComplete();

    verify(transactionRepository).delete(transaction);
  }

  @Test
  @DisplayName("Falla al eliminar una transacción inexistente")
  void failsDeletingMissingTransaction() {
    when(transactionRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(transactionCommandService.deleteTransactionById(USER_ID, 99L))
        .expectError(TransactionNotFoundException.class)
        .verify();

    verify(transactionRepository, never()).delete(any(Transaction.class));
  }

  @Test
  @DisplayName("Asigna la transacción creada al usuario que la registra")
  void assignsCreatedTransactionToRequestingUser() {
    givenValidReferences();

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID, createRequest(null)))
        .assertNext(saved -> assertEquals(USER_ID, saved.getUserId()))
        .verifyComplete();
  }

  @Test
  @DisplayName("No deja actualizar la transacción de otro usuario")
  void refusesToUpdateAnotherUsersTransaction() {
    when(transactionRepository.findByIdAndUserId(10L, OTHER_USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(transactionCommandService.updateTransaction(OTHER_USER_ID, 10L,
        new UpdateTransactionRequest()))
        .expectError(TransactionNotFoundException.class)
        .verify();

    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  @DisplayName("No deja eliminar la transacción de otro usuario")
  void refusesToDeleteAnotherUsersTransaction() {
    when(transactionRepository.findByIdAndUserId(10L, OTHER_USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(transactionCommandService.deleteTransactionById(OTHER_USER_ID, 10L))
        .expectError(TransactionNotFoundException.class)
        .verify();

    verify(transactionRepository, never()).delete(any(Transaction.class));
  }

  @Test
  @DisplayName("Reutiliza el tag que el usuario ya tiene aunque lo escriba con otra grafia")
  void reusesExistingTagWrittenWithAnotherCase() {
    givenValidReferences();
    doReturn(Mono.just(0L)).when(tagRepository).insertIfAbsent(USER_ID, "casa");
    doReturn(Flux.just(new Tag(55L, USER_ID, "Casa")))
        .when(tagRepository).findByUserIdAndLowerNameIn(eq(USER_ID), any());

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID,
        createRequest(List.of("casa"))))
        .expectNextCount(1)
        .verifyComplete();

    assertEquals(List.of(55L), capturedLinkedTagIds());
    verify(tagRepository, never()).save(any(Tag.class));
  }

  @Test
  @DisplayName("Enlaza la transaccion con los tags que resolvio el catalogo")
  void linksTransactionToResolvedTags() {
    givenValidReferences();

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID,
        createRequest(List.of("ocio", "personal"))))
        .expectNextCount(1)
        .verifyComplete();

    assertEquals(2, capturedLinkedTagIds().size());
  }

  @Test
  @DisplayName("No da de alta ningun tag cuando la lista queda vacia tras normalizar")
  void doesNotTouchCatalogWhenNoTagsRemain() {
    givenValidReferences();

    StepVerifier.create(transactionCommandService.createTransaction(USER_ID,
        createRequest(List.of("   ", ""))))
        .expectNextCount(1)
        .verifyComplete();

    verify(tagRepository, never()).insertIfAbsent(anyLong(), anyString());
    verify(transactionTagRepository, never()).saveAll(any(Iterable.class));
  }

  /**
   * Construye la transacción existente usada en las pruebas de actualización.
   *
   * @return la transacción existente
   */
  private Transaction existingTransaction() {
    return new Transaction(10L, new BigDecimal("300.00"), "Original",
        LocalDateTime.of(2026, 4, 26, 13, 35), USER_ID, 2L, 3L, null);
  }
}
