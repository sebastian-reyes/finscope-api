package com.sreyes.finscope.exception.handler;

import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.exception.response.ErrorResponse;
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
}
