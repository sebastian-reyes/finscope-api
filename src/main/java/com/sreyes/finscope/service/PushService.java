package com.sreyes.finscope.service;

import com.sreyes.finscope.model.query.NotificationSettings;
import com.sreyes.finscope.model.query.PushMessage;
import reactor.core.publisher.Mono;

/**
 * Servicio de los avisos al teléfono: suscripciones, preferencias y envío.
 *
 * Un aviso no lo decide el teléfono sino el servidor, y llega aunque la aplicación esté
 * cerrada: el navegador entrega una suscripción al activarlos, y a partir de ahí la API manda
 * cada mensaje cifrado al servicio de push de ese navegador, que se lo hace llegar.
 */
public interface PushService {

  /**
   * Indica si el servidor tiene claves con las que mandar avisos.
   *
   * @return si los avisos están disponibles
   */
  boolean enabled();

  /**
   * Clave pública VAPID que el navegador necesita para suscribirse.
   *
   * @return la clave en Base64 URL, o nulo si los avisos no están disponibles
   */
  String publicKey();

  /**
   * Guarda la suscripción de un dispositivo del usuario.
   *
   * @param userId   identificador del usuario
   * @param endpoint dirección del servicio de push del dispositivo
   * @param p256dh   clave pública del navegador
   * @param auth     secreto de autenticación del navegador
   * @return Mono vacío al completar
   */
  Mono<Void> subscribe(Long userId, String endpoint, String p256dh, String auth);

  /**
   * Da de baja un dispositivo del usuario. No falla si ya no estaba.
   *
   * @param userId   identificador del usuario
   * @param endpoint dirección de la suscripción
   * @return Mono vacío al completar
   */
  Mono<Void> unsubscribe(Long userId, String endpoint);

  /**
   * Obtiene qué avisos quiere el usuario.
   *
   * @param userId identificador del usuario
   * @return sus preferencias
   */
  Mono<NotificationSettings> findSettings(Long userId);

  /**
   * Cambia qué avisos quiere el usuario. Lo que llega nulo no se toca.
   *
   * @param userId       identificador del usuario
   * @param recurringDue avisos de fijos por vencer
   * @param budgetLimit  avisos de presupuestos al límite
   * @return las preferencias tal y como quedan
   */
  Mono<NotificationSettings> updateSettings(Long userId, Boolean recurringDue,
                                            Boolean budgetLimit);

  /**
   * Manda un aviso de prueba a todos los dispositivos del usuario.
   *
   * @param userId identificador del usuario
   * @return cuántos dispositivos lo aceptaron
   */
  Mono<Integer> sendTest(Long userId);

  /**
   * Manda un aviso a todos los dispositivos de un usuario, dando de baja los que ya no
   * existen.
   *
   * @param userId  destinatario
   * @param message aviso
   * @return cuántos lo aceptaron y cuántos fallaron por algo que puede reintentarse
   */
  Mono<Delivery> notifyUser(Long userId, PushMessage message);

  /**
   * Resultado de mandar un aviso a los dispositivos de un usuario.
   *
   * @param delivered dispositivos cuyo servicio aceptó el mensaje
   * @param retryable dispositivos que fallaron por un problema pasajero
   */
  record Delivery(int delivered, int retryable) {
  }
}
