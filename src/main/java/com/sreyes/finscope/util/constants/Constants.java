package com.sreyes.finscope.util.constants;

import lombok.experimental.UtilityClass;

/**
 * Clase de utilidades que contiene constantes usadas en la aplicación FinScope.<br/>
 * Esta clase no debe ser instanciada.
 */
@UtilityClass
public final class Constants {

  public static final String TRANSACTION_NOT_FOUND = "Transaction not found with id: ";
  public static final String TRANSACTION_TYPE_NOT_FOUND = "Transaction type not found with id: ";
  public static final String CATEGORY_NOT_FOUND = "Category not found with id: ";
  public static final String INVALID_MONTH = "Invalid month. Month must be between 1 and 12.";
  public static final String CATEGORY_ALREADY_EXISTS = "Category with name {} already exists";
}
