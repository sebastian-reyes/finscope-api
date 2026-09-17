package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al pedir algo de los avisos al teléfono en un servidor
 * sin claves VAPID. Es un 503 y no un 400: la petición es correcta, lo que falta es la
 * configuración del servidor, y otro servidor con claves la atendería.
 */
public class PushNotConfiguredException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code PushNotConfiguredException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public PushNotConfiguredException(String message) {
    super(message, "PUSH_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE);
  }
}
