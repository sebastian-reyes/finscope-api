package com.sreyes.finscope.exception.custom;

public class CategoryNotFoundException extends RuntimeException{
  public CategoryNotFoundException(String message) {
    super(message);
  }
}