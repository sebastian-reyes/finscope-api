package com.sreyes.finscope.model.query;

/**
 * Nombre de un tag junto a la transacción que lo lleva.
 * Es la proyección que devuelve la carga en lote de los tags de una página: al consultar
 * se necesita el nombre y la transacción a la que pertenece, no la entidad del tag ni la
 * del enlace, y así el ensamblado de la respuesta agrupa sin tener que resolver nada más.
 *
 * El nombre de la propiedad evita a propósito llamarse `name`: coincidiría con la propiedad
 * homónima de {@link com.sreyes.finscope.model.entity.Tag}, que está mapeada a la columna
 * `name_tag`, y el conversor resolvería la columna equivocada dejando el valor a nulo.
 *
 * @param transactionId identificador de la transacción que lleva el tag
 * @param tagName       nombre del tag
 */
public record TransactionTagName(Long transactionId, String tagName) {
}
