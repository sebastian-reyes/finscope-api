package com.sreyes.finscope.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreyes.finscope.model.query.CategoryUsage;
import com.sreyes.finscope.model.query.RecurringTagName;
import com.sreyes.finscope.model.query.TagUsage;
import com.sreyes.finscope.model.query.TransactionTagName;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de {@link CategoryRepository} y {@link TagRepository} contra PostgreSQL.
 *
 * <p>Las dos devuelven proyecciones que conviven con una entidad de propiedades parecidas
 * —nombre, color, icono—, que es exactamente la situación en la que Spring Data R2DBC
 * confunde un componente del record con la columna de la entidad y lo deja todo a nulo.
 * Pasó con los tags; aquí se fija que no vuelva a pasar con ninguna.</p>
 */
class CatalogueRepositoryTest extends PostgresRepositoryTest {

  private static final LocalDateTime DATE = LocalDateTime.of(2026, 8, 10, 12, 0);

  @Autowired
  private CategoryRepository categories;

  @Autowired
  private TagRepository tags;

  /** El uso de cada categoría sale completo y cuenta solo sus movimientos. */
  @Test
  void categoryUsageIsFullyPopulatedAndCounted() {
    long userId = user();
    long food = category(userId, "Comida");
    long empty = category(userId, "Viajes");
    db.sql("UPDATE categories SET color = 'preset-3', icon = 'basket' WHERE id_category = :id")
        .bind("id", food).then().block();
    transaction(userId, food, EXPENSE, "10.00", DATE);
    transaction(userId, food, EXPENSE, "20.00", DATE);

    List<CategoryUsage> usage = categories.findUsageByUserId(userId).collectList().block();
    CategoryUsage one = categories.findUsageByUserIdAndId(userId, food).block();

    assertThat(usage).extracting(CategoryUsage::categoryId).containsExactly(food, empty);
    assertThat(one).hasNoNullFieldsOrProperties();
    assertThat(one.categoryName()).isEqualTo("Comida");
    assertThat(one.categoryScope()).isEqualTo("BOTH");
    assertThat(one.systemCategory()).isFalse();
    assertThat(one.categoryColor()).isEqualTo("preset-3");
    assertThat(one.categoryIcon()).isEqualTo("basket");
    assertThat(one.transactionCount()).isEqualTo(2L);
    assertThat(usage.get(1).transactionCount()).isZero();
  }

  /** El uso de cada tag sale completo y no se mezcla con los de otro usuario. */
  @Test
  void tagUsageIsFullyPopulatedAndCounted() {
    long userId = user();
    long food = category(userId, "Comida");
    long work = tag(userId, "Trabajo");
    db.sql("UPDATE tags SET color = '#e8a33d', icon = 'briefcase' WHERE id_tag = :id")
        .bind("id", work).then().block();
    tagTransaction(transaction(userId, food, EXPENSE, "10.00", DATE), work);
    long stranger = user();
    tag(stranger, "Ajeno");

    List<TagUsage> usage = tags.findUsageByUserId(userId).collectList().block();

    assertThat(usage).singleElement().hasNoNullFieldsOrProperties();
    TagUsage only = usage.get(0);
    assertThat(only.tagId()).isEqualTo(work);
    assertThat(only.tagName()).isEqualTo("Trabajo");
    assertThat(only.tagColor()).isEqualTo("#e8a33d");
    assertThat(only.tagIcon()).isEqualTo("briefcase");
    assertThat(only.transactionCount()).isEqualTo(1L);
  }

  /** Los nombres de los tags de cada movimiento y de cada fijo llegan con su dueño. */
  @Test
  void tagNamesArriveWithTheirOwner() {
    long userId = user();
    long food = category(userId, "Comida");
    long work = tag(userId, "Trabajo");
    long transactionId = transaction(userId, food, EXPENSE, "10.00", DATE);
    tagTransaction(transactionId, work);
    long recurringId = recurring(userId, food, "10.00", 5, 1, 1, 2026);
    db.sql("INSERT INTO recurring_tags (recurring_id, tag_id) VALUES (:recurringId, :tagId)")
        .bind("recurringId", recurringId).bind("tagId", work).then().block();

    List<TransactionTagName> transactionTags =
        tags.findNamesByTransactionIdIn(List.of(transactionId)).collectList().block();
    List<RecurringTagName> recurringTags =
        tags.findNamesByRecurringIdIn(List.of(recurringId)).collectList().block();

    assertThat(transactionTags)
        .containsExactly(new TransactionTagName(transactionId, "Trabajo"));
    assertThat(recurringTags).containsExactly(new RecurringTagName(recurringId, "Trabajo"));
  }

  /** Los nombres no distinguen mayúsculas, igual que la restricción de unicidad. */
  @Test
  void namesAreCaseInsensitive() {
    long userId = user();
    long work = tag(userId, "Trabajo");
    long food = category(userId, "Comida");

    assertThat(tags.findByUserIdAndName(userId, "TRABAJO").block().getId()).isEqualTo(work);
    assertThat(categories.findByUserIdAndName(userId, "comida").block().getId()).isEqualTo(food);
    assertThat(tags.insertIfAbsent(userId, "trabajo").block()).isZero();
    assertThat(categories.insertIfAbsent(userId, "COMIDA", "EXPENSE", false).block()).isZero();
  }
}
