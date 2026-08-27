package com.sreyes.finscope.model.query;

/**
 * Tag del catálogo de un usuario junto con el uso que le está dando.
 * El número de transacciones lo calcula la base de datos al listar, en lugar de consultarse
 * tag a tag, y es lo que permite al cliente ordenar el catálogo por uso y avisar de lo que
 * se pierde al borrar un tag.
 *
 * Los nombres de las propiedades evitan a propósito llamarse `id` y `name`, por el mismo
 * motivo que {@link TransactionTagName}: coincidirían con las propiedades homónimas de
 * {@link com.sreyes.finscope.model.entity.Tag}, mapeadas a las columnas `id_tag` y
 * `name_tag`, y el conversor resolvería las columnas equivocadas dejando toda la proyección
 * a nulo.
 *
 * @param tagId            identificador del tag
 * @param tagName          nombre del tag, con la grafía que escribió el usuario
 * @param transactionCount cuántas transacciones del usuario llevan el tag, cero si ninguna
 */
public record TagUsage(Long tagId, String tagName, Long transactionCount) {
}
