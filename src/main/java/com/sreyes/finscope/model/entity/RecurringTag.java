package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que enlaza un movimiento fijo con uno de sus tags.
 * Está mapeada a la tabla `recurring_tags` en la base de datos y materializa la relación
 * muchos a muchos entre {@link RecurringTransaction} y {@link Tag}.
 *
 * Los tags viven en la plantilla y no en cada mes: se escriben una vez y se copian al
 * movimiento cada vez que se confirma, de modo que el historial sale clasificado igual que
 * si se hubiera registrado a mano. Pedirlos al confirmar sería escribir lo mismo doce veces
 * al año, y el mes que se olvidara quedaría sin contexto sin que nada avisara.
 *
 * Apunta al mismo catálogo `tags` del usuario que {@link TransactionTag}: un tag no
 * significa una cosa en un fijo y otra en un movimiento, y dos catálogos que hay que
 * mantener iguales acaban siendo dos que no lo están.
 *
 * Lleva clave subrogada por el mismo motivo que el enlace de las transacciones: el
 * repositorio reactivo necesita un identificador para insertar, y la unicidad real la
 * garantiza la restricción `uq_recurring_tags` de la base de datos.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("recurring_tags")
public class RecurringTag {

  @Id
  @Column("id_recurring_tag")
  private Long id;

  @Column("recurring_id")
  private Long recurringId;

  @Column("tag_id")
  private Long tagId;
}
