package com.sreyes.finscope.model.query;

/**
 * Nombre de un tag junto al movimiento fijo que lo lleva.
 * Es la proyección que devuelve la carga en lote de los tags de una lista de plantillas: al
 * consultar se necesita el nombre y la plantilla a la que pertenece, no la entidad del tag
 * ni la del enlace, y así el ensamblado de la respuesta agrupa sin lanzar una consulta por
 * cada fijo de la pantalla.
 *
 * El nombre de la propiedad evita a propósito llamarse `name`, por lo mismo que en
 * {@link TransactionTagName}: coincidiría con la propiedad homónima de
 * {@link com.sreyes.finscope.model.entity.Tag}, que está mapeada a la columna `name_tag`, y
 * el conversor resolvería la columna equivocada dejando el valor a nulo.
 *
 * @param recurringId identificador de la plantilla que lleva el tag
 * @param tagName     nombre del tag
 */
public record RecurringTagName(Long recurringId, String tagName) {
}
