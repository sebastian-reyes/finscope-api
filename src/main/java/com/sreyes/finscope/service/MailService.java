package com.sreyes.finscope.service;

import reactor.core.publisher.Mono;

/**
 * Envío de los correos con los que la aplicación habla con el dueño de una cuenta.
 * Son cuatro y ninguno es informativo: tres llevan un enlace que hace algo al pulsarlo y el
 * cuarto avisa de un cambio que otro acaba de pedir.
 *
 * <p>El servicio recibe el valor del enlace, no la dirección completa: componer la URL es
 * decisión de presentación del correo y depende de dónde viva la aplicación web, que es algo
 * que el resto del dominio no tiene por qué saber.</p>
 */
public interface MailService {

  /**
   * Manda el enlace con el que se da por verificada la dirección de la cuenta.
   *
   * @param to          dirección de la cuenta
   * @param displayName nombre del usuario, si lo tiene puesto
   * @param token       valor del enlace de un solo uso
   * @return Mono vacío al completar el envío
   */
  Mono<Void> sendEmailVerification(String to, String displayName, String token);

  /**
   * Manda el enlace con el que se establece una contraseña nueva.
   *
   * @param to          dirección de la cuenta
   * @param displayName nombre del usuario, si lo tiene puesto
   * @param token       valor del enlace de un solo uso
   * @return Mono vacío al completar el envío
   */
  Mono<Void> sendPasswordReset(String to, String displayName, String token);

  /**
   * Manda a la dirección nueva el enlace que confirma el cambio de correo.
   *
   * @param to          dirección nueva propuesta
   * @param displayName nombre del usuario, si lo tiene puesto
   * @param token       valor del enlace de un solo uso
   * @return Mono vacío al completar el envío
   */
  Mono<Void> sendEmailChange(String to, String displayName, String token);

  /**
   * Avisa a la dirección anterior de que se ha pedido mover la cuenta a otra.
   * No lleva enlace: es el aviso por el que el dueño legítimo se entera de un cambio que no
   * ha pedido, mientras el cambio todavía no se ha aplicado.
   *
   * @param to          dirección actual de la cuenta
   * @param displayName nombre del usuario, si lo tiene puesto
   * @param newEmail    dirección a la que se quiere mover la cuenta
   * @return Mono vacío al completar el envío
   */
  Mono<Void> sendEmailChangeNotice(String to, String displayName, String newEmail);
}
