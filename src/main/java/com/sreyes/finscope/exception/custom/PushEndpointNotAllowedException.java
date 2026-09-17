package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando la dirección de una suscripción no es de un
 * servicio de push conocido. La API hace peticiones a esa dirección, así que aceptar
 * cualquiera permitiría usar el servidor para llamar a donde uno quisiera.
 */
public class PushEndpointNotAllowedException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code PushEndpointNotAllowedException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public PushEndpointNotAllowedException(String message) {
    super(message, "PUSH_ENDPOINT_NOT_ALLOWED", HttpStatus.BAD_REQUEST);
  }
}
