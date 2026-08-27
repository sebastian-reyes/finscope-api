package com.sreyes.finscope.model.query;

/**
 * Categoría del catálogo de un usuario junto con el uso que le está dando.
 * El número de transacciones lo calcula la base de datos al listar, en lugar de
 * consultarse categoría a categoría, y es lo que permite avisar de cuántos movimientos se
 * reasignarán antes de borrar una.
 *
 * Los nombres de las propiedades evitan a propósito llamarse `id`, `name` o `appliesTo`,
 * por el mismo motivo que {@link TagUsage}: coincidirían con las propiedades homónimas de
 * {@link com.sreyes.finscope.model.entity.Category} y el conversor iría a buscar las
 * columnas de la entidad en lugar de los alias de la consulta, dejando toda la proyección
 * a nulo sin dar ningún error.
 *
 * @param categoryId       identificador de la categoría
 * @param categoryName     nombre de la categoría, con la grafía que escribió el usuario
 * @param categoryScope    tipo de movimiento al que se ofrece: EXPENSE, INCOME o BOTH
 * @param systemCategory   si es la categoría de reserva, que no puede eliminarse
 * @param transactionCount cuántas transacciones clasifica, cero si ninguna
 */
public record CategoryUsage(
    Long categoryId,
    String categoryName,
    String categoryScope,
    Boolean systemCategory,
    Long transactionCount) {
}
