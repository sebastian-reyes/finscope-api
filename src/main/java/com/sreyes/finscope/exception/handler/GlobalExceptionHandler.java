package com.sreyes.finscope.exception.handler;

import com.sreyes.finscope.api.model.ErrorResponse;
import com.sreyes.finscope.exception.custom.BusinessException;
import com.sreyes.finscope.util.constants.Constants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

/**
 * Manejador global de excepciones para la aplicación.
 * Traduce las excepciones de negocio y las de entrada de la petición a la respuesta de
 * error estructurada definida en el contrato OpenAPI, con un código estable que permite
 * a los clientes reaccionar sin depender del mensaje.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final Clock clock;

  /**
   * Maneja cualquier excepción de negocio de la aplicación.
   * El estado HTTP y el código de la respuesta los aporta la propia excepción, de modo
   * que agregar una nueva regla de negocio no obliga a tocar este manejador.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error con detalles del incidente.
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
    return buildResponse(ex.getStatus(), ex.getCode(), ex.getMessage());
  }

  /**
   * Maneja los fallos de validación de los objetos de petición anotados con Jakarta Validation.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error que detalla los campos inválidos.
   */
  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(WebExchangeBindException ex) {
    String message = ex.getFieldErrors().stream()
        .map(this::formatFieldError)
        .collect(Collectors.joining("; "));
    return buildResponse(HttpStatus.BAD_REQUEST, Constants.VALIDATION_FAILED, message);
  }

  /**
   * Maneja los fallos de validación de los parámetros de consulta y de ruta.
   * Las interfaces generadas a partir del contrato OpenAPI están anotadas con
   * {@code @Validated}, por lo que las restricciones de sus parámetros se notifican como
   * violaciones de Jakarta Validation en lugar de como errores de enlace.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error que detalla los parámetros inválidos.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolationException(
      ConstraintViolationException ex) {
    String message = ex.getConstraintViolations().stream()
        .map(this::formatViolation)
        .collect(Collectors.joining("; "));
    return buildResponse(HttpStatus.BAD_REQUEST, Constants.VALIDATION_FAILED, message);
  }

  /**
   * Maneja las peticiones cuyo cuerpo o parámetros no pueden interpretarse, por ejemplo
   * cuando falta un parámetro obligatorio o un valor tiene un tipo incorrecto.
   * El motivo original se registra pero no se devuelve: lo redacta el deserializador y
   * suele nombrar clases y campos internos, que describen la implementación y no lo que el
   * cliente ha hecho mal. Los errores que sí puede corregir —campos fuera de rango, formatos
   * inválidos— llegan por la validación y sí detallan el campo.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error con detalles del incidente.
   */
  @ExceptionHandler(ServerWebInputException.class)
  public ResponseEntity<ErrorResponse> handleServerWebInputException(ServerWebInputException ex) {
    if (isPayloadTooLarge(ex)) {
      return handlePayloadTooLarge();
    }
    log.debug("Rejected malformed request: {}", ex.getReason());
    return buildResponse(HttpStatus.BAD_REQUEST, Constants.INVALID_REQUEST,
        Constants.MALFORMED_REQUEST);
  }

  /**
   * Maneja los cuerpos que superan el tamaño admitido.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error indicando que la petición es demasiado grande.
   */
  @ExceptionHandler(DataBufferLimitException.class)
  public ResponseEntity<ErrorResponse> handleDataBufferLimitException(DataBufferLimitException ex) {
    log.warn("Rejected oversized request body");
    return handlePayloadTooLarge();
  }

  /**
   * Maneja las excepciones que ya definen un estado HTTP propio, como el acceso a una ruta
   * inexistente.
   * Se responde con la descripción estándar del estado en lugar de con el motivo que trae
   * la excepción, que puede incluir la ruta o el recurso solicitado.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error con el estado indicado por la excepción.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    log.debug("Request rejected with status {}: {}", status.value(), ex.getReason());
    return buildResponse(status, status.name(), status.getReasonPhrase());
  }

  /**
   * Maneja cualquier excepción no contemplada para evitar exponer detalles internos.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error genérica.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
    log.error("Unexpected error while handling the request", ex);
    return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, Constants.INTERNAL_ERROR,
        Constants.UNEXPECTED_ERROR);
  }

  /**
   * Compone la respuesta de una petición cuyo cuerpo excede el tamaño admitido.
   *
   * @return la respuesta de error correspondiente.
   */
  private ResponseEntity<ErrorResponse> handlePayloadTooLarge() {
    return buildResponse(HttpStatus.CONTENT_TOO_LARGE, Constants.PAYLOAD_TOO_LARGE_CODE,
        Constants.PAYLOAD_TOO_LARGE);
  }

  /**
   * Indica si el fallo de entrada se debe a que el cuerpo supera el tamaño admitido.
   *
   * @param ex la excepción lanzada.
   * @return {@code true} si la causa es el límite de tamaño.
   */
  private boolean isPayloadTooLarge(Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof DataBufferLimitException) {
        return true;
      }
      if (cause.getCause() == cause) {
        return false;
      }
    }
    return false;
  }

  /**
   * Construye la respuesta de error a partir del estado, el código y el mensaje indicados.
   *
   * @param status  estado HTTP de la respuesta.
   * @param code    código estable del error.
   * @param message mensaje descriptivo del error.
   * @return la respuesta de error lista para devolverse al cliente.
   */
  private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code,
                                                      String message) {
    ErrorResponse body = new ErrorResponse(LocalDateTime.now(clock), status.value(), code, message);
    return ResponseEntity.status(status).body(body);
  }

  /**
   * Formatea la violación de un parámetro como `parámetro: motivo`, descartando el nombre
   * del método que antecede al parámetro en la ruta de la propiedad.
   *
   * @param violation violación de una restricción de validación.
   * @return el mensaje formateado.
   */
  private String formatViolation(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    String parameter = path.substring(path.lastIndexOf('.') + 1);
    return parameter + ": " + violation.getMessage();
  }

  /**
   * Formatea el error de un campo como `campo: motivo`.
   *
   * @param fieldError error de validación de un campo.
   * @return el mensaje formateado.
   */
  private String formatFieldError(FieldError fieldError) {
    return fieldError.getField() + ": " + fieldError.getDefaultMessage();
  }
}
