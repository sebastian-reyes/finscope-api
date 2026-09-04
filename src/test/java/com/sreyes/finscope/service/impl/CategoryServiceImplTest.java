package com.sreyes.finscope.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.exception.custom.CategoryNameAlreadyUsedException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.SystemCategoryException;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.query.CategoryUsage;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.repository.RecurringTransactionRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link CategoryServiceImpl}, centradas en que el catálogo se pueda
 * editar entero sin que ninguna transacción se quede sin categoría por el camino.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceImplTest {

  private static final Long USER_ID = 7L;

  @Mock
  private CategoryRepository categoryRepository;

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private RecurringTransactionRepository recurringTransactionRepository;

  @InjectMocks
  private CategoryServiceImpl categoryService;

  /** Categoría de reserva del usuario, la que recibe lo que se elimina. */
  private Category fallback() {
    return new Category(1L, USER_ID, "Otros", "BOTH", true);
  }

  /** Categoría corriente, editable y eliminable. */
  private Category comida() {
    return new Category(4L, USER_ID, "Comida", "EXPENSE", false);
  }

  @Test
  @DisplayName("Crea una categoría con el ámbito indicado y sin transacciones")
  void createsCategory() {
    when(categoryRepository.findByUserIdAndName(USER_ID, "Mascotas")).thenReturn(Mono.empty());
    when(categoryRepository.insertIfAbsent(eq(USER_ID), eq("Mascotas"), eq("EXPENSE"),
        eq(false))).thenReturn(Mono.just(1L));
    when(categoryRepository.findByUserIdAndName(USER_ID, "Mascotas"))
        .thenReturn(Mono.empty(), Mono.just(new Category(9L, USER_ID, "Mascotas", "EXPENSE",
            false)));

    StepVerifier.create(categoryService.createCategory(USER_ID, " Mascotas ",
        CategoryScope.EXPENSE))
        .assertNext(created -> {
          assertEquals("Mascotas", created.categoryName());
          assertEquals(0L, created.transactionCount());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Rechaza un nombre que el usuario ya tiene")
  void rejectsDuplicatedName() {
    when(categoryRepository.findByUserIdAndName(USER_ID, "Comida"))
        .thenReturn(Mono.just(comida()));

    StepVerifier.create(categoryService.createCategory(USER_ID, "Comida", CategoryScope.EXPENSE))
        .expectError(CategoryNameAlreadyUsedException.class)
        .verify();

    verify(categoryRepository, never()).insertIfAbsent(anyLong(), anyString(), anyString(),
        anyBoolean());
  }

  @Test
  @DisplayName("Renombra la categoría conservando su ámbito cuando no se informa")
  void renamesKeepingScope() {
    when(categoryRepository.findByIdAndUserId(4L, USER_ID)).thenReturn(Mono.just(comida()));
    when(categoryRepository.findByUserIdAndName(USER_ID, "Alimentación")).thenReturn(Mono.empty());
    when(categoryRepository.save(any(Category.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(categoryRepository.findUsageByUserIdAndId(USER_ID, 4L))
        .thenReturn(Mono.just(new CategoryUsage(4L, "Alimentación", "EXPENSE", false, 12L)));

    StepVerifier.create(categoryService.updateCategory(USER_ID, 4L, "Alimentación", null))
        .assertNext(updated -> {
          assertEquals("Alimentación", updated.categoryName());
          assertEquals("EXPENSE", updated.categoryScope());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Falla al editar una categoría de otro usuario")
  void failsUpdatingAnotherUsersCategory() {
    when(categoryRepository.findByIdAndUserId(4L, 8L)).thenReturn(Mono.empty());

    StepVerifier.create(categoryService.updateCategory(8L, 4L, "Comida", null))
        .expectError(CategoryNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Al eliminar una categoría, sus movimientos pasan a la de reserva")
  void reassignsTransactionsBeforeDeleting() {
    Category comida = comida();
    when(categoryRepository.findByIdAndUserId(4L, USER_ID)).thenReturn(Mono.just(comida));
    when(categoryRepository.findSystemByUserId(USER_ID)).thenReturn(Mono.just(fallback()));
    when(transactionRepository.reassignCategory(USER_ID, 4L, 1L)).thenReturn(Mono.just(12L));
    when(recurringTransactionRepository.reassignCategory(USER_ID, 4L, 1L))
        .thenReturn(Mono.just(2L));
    when(categoryRepository.delete(comida)).thenReturn(Mono.empty());

    StepVerifier.create(categoryService.deleteCategory(USER_ID, 4L))
        .verifyComplete();

    verify(transactionRepository).reassignCategory(USER_ID, 4L, 1L);
    verify(categoryRepository).delete(comida);
  }

  @Test
  @DisplayName("Al eliminar una categoría, sus movimientos fijos también pasan a la de reserva")
  void reassignsRecurringBeforeDeleting() {
    Category comida = comida();
    when(categoryRepository.findByIdAndUserId(4L, USER_ID)).thenReturn(Mono.just(comida));
    when(categoryRepository.findSystemByUserId(USER_ID)).thenReturn(Mono.just(fallback()));
    when(transactionRepository.reassignCategory(USER_ID, 4L, 1L)).thenReturn(Mono.just(12L));
    when(recurringTransactionRepository.reassignCategory(USER_ID, 4L, 1L))
        .thenReturn(Mono.just(2L));
    when(categoryRepository.delete(comida)).thenReturn(Mono.empty());

    StepVerifier.create(categoryService.deleteCategory(USER_ID, 4L))
        .verifyComplete();

    // La cascada de la base los borraría; se mueven antes para que reordenar el catálogo no
    // se lleve por delante el alquiler.
    verify(recurringTransactionRepository).reassignCategory(USER_ID, 4L, 1L);
    verify(categoryRepository).delete(comida);
  }

  @Test
  @DisplayName("No deja eliminar la categoría de reserva")
  void refusesToDeleteTheFallbackCategory() {
    when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Mono.just(fallback()));

    StepVerifier.create(categoryService.deleteCategory(USER_ID, 1L))
        .expectError(SystemCategoryException.class)
        .verify();

    verify(transactionRepository, never()).reassignCategory(anyLong(), anyLong(), anyLong());
    verify(categoryRepository, never()).delete(any(Category.class));
  }

  @Test
  @DisplayName("Crea la categoría de reserva al vuelo si la cuenta no la tiene")
  void createsTheFallbackWhenMissing() {
    Category comida = comida();
    when(categoryRepository.findByIdAndUserId(4L, USER_ID)).thenReturn(Mono.just(comida));
    when(categoryRepository.findSystemByUserId(USER_ID))
        .thenReturn(Mono.empty(), Mono.just(fallback()));
    when(categoryRepository.insertIfAbsent(USER_ID, "Otros", "BOTH", true))
        .thenReturn(Mono.just(1L));
    when(transactionRepository.reassignCategory(USER_ID, 4L, 1L)).thenReturn(Mono.just(0L));
    when(recurringTransactionRepository.reassignCategory(USER_ID, 4L, 1L))
        .thenReturn(Mono.just(0L));
    when(categoryRepository.delete(comida)).thenReturn(Mono.empty());

    StepVerifier.create(categoryService.deleteCategory(USER_ID, 4L))
        .verifyComplete();

    verify(categoryRepository).insertIfAbsent(USER_ID, "Otros", "BOTH", true);
  }

  @Test
  @DisplayName("Siembra el catálogo inicial encabezado por la categoría de reserva")
  void seedsTheInitialCatalogue() {
    when(categoryRepository.findSystemByUserId(USER_ID)).thenReturn(Mono.just(fallback()));
    when(categoryRepository.insertIfAbsent(anyLong(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Mono.just(1L));

    StepVerifier.create(categoryService.seedDefaults(USER_ID))
        .verifyComplete();

    verify(categoryRepository).insertIfAbsent(USER_ID, "Comida", "EXPENSE", false);
    verify(categoryRepository).insertIfAbsent(USER_ID, "Salario", "INCOME", false);
    verify(categoryRepository, times(13))
        .insertIfAbsent(eq(USER_ID), anyString(), anyString(), eq(false));
  }
}
