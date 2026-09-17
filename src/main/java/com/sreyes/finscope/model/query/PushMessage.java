package com.sreyes.finscope.model.query;

/**
 * Un aviso listo para mandarse a los dispositivos de un usuario.
 *
 * @param title título, lo que se lee en la pantalla bloqueada
 * @param body  detalle, una línea
 * @param url   pantalla de la aplicación que se abre al tocarlo, relativa al origen
 * @param tag   etiqueta del aviso: uno nuevo con la misma sustituye al anterior en el teléfono
 *              en lugar de amontonarse, así que el «vence hoy» reemplaza al «vence mañana»
 */
public record PushMessage(String title, String body, String url, String tag) {
}
