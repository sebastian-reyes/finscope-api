package com.sreyes.finscope.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

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
}
