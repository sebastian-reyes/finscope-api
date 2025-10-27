package com.sreyes.finscope.util.constants;

/**
 * Clase de utilidades que contiene constantes usadas en la aplicación FinScope.<br/>
 * Esta clase no debe ser instanciada.
 */
public final class Constants {

  private Constants() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static final String TRANSACTION_NOT_FOUND = "Transaction not found with id: ";
  public static final String TRANSACTION_TYPE_NOT_FOUND = "Transaction type not found with id: ";
  public static final String CATEGORY_NOT_FOUND = "Category not found with id: ";
  public static final String INVALID_MONTH = "Invalid month. Month must be between 1 and 12.";
}
