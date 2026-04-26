package com.sreyes.finscope.exception.custom;

public class DuplicateCategoryException extends RuntimeException {
  public DuplicateCategoryException(String message) {
    super(message);
  }
}