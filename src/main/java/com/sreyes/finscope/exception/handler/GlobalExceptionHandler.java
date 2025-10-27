package com.sreyes.finscope.exception.handler;

import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.DateNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.exception.response.ErrorResponse;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Manejador global de excepciones para la aplicación.
 * Captura excepciones personalizadas y retorna una respuesta de error estructurada.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Maneja la excepción {@link CategoryNotFoundException} cuando no se encuentra una categoría.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error con detalles del incidente.
   */
  @ExceptionHandler(CategoryNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  public ErrorResponse handleCategoryNotFoundException(CategoryNotFoundException ex) {
    return new ErrorResponse(
        ex.getMessage(),
        LocalDateTime.now(),
        HttpStatus.NOT_FOUND.value()
    );
  }

  /**
   * Maneja la excepción {@link TransactionTypeNotFoundException} cuando
   * no se encuentra un tipo de transacción.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error con detalles del incidente.
   */
  @ExceptionHandler(TransactionTypeNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  public ErrorResponse handleTransactionTypeNotFoundException(TransactionTypeNotFoundException ex) {
    return new ErrorResponse(
        ex.getMessage(),
        LocalDateTime.now(),
        HttpStatus.NOT_FOUND.value()
    );
  }

  /**
   * Maneja la excepción {@link TransactionNotFoundException} cuando
   * no se encuentra una transacción.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error con detalles del incidente.
   */
  @ExceptionHandler(TransactionNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  public ErrorResponse handleTransactionNotFoundException(TransactionNotFoundException ex) {
    return new ErrorResponse(
        ex.getMessage(),
        LocalDateTime.now(),
        HttpStatus.NOT_FOUND.value()
    );
  }

  /**
   * Maneja la excepción {@link DateNotFoundException} cuando no se encuentra una fecha específica.
   *
   * @param ex la excepción lanzada.
   * @return una respuesta de error con detalles del incidente.
   */
  @ExceptionHandler(DateNotFoundException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse handleDateNotFoundException(DateNotFoundException ex) {
    return new ErrorResponse(
        ex.getMessage(),
        LocalDateTime.now(),
        HttpStatus.BAD_REQUEST.value()
    );
  }
}