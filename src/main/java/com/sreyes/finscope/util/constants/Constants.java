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
  public static final String TAG_NOT_FOUND = "Tag not found with id: ";
  public static final String TAG_NAME_ALREADY_USED = "A tag named {} already exists";
  public static final String CATEGORY_NOT_FOUND = "Category not found with id: ";
  public static final String CATEGORY_NAME_ALREADY_USED = "A category named {} already exists";
  public static final String CATEGORY_NOT_APPLICABLE =
      "Category {} cannot be used on this kind of transaction";
  public static final String SYSTEM_CATEGORY_PROTECTED =
      "The fallback category cannot be deleted: it receives the transactions of the categories "
          + "you remove";

  public static final String INVALID_MONTH = "Invalid month. Month must be between 1 and 12.";
  public static final String INVALID_DATE_RANGE = "Invalid date range. dateFrom must be before dateTo.";
  public static final String CONFLICTING_DATE_FILTERS =
      "Filters month and year cannot be combined with dateFrom or dateTo.";
  public static final String INCOMPLETE_MONTH_FILTER =
      "Filters month and year must be provided together.";
  public static final String INVALID_SORT =
      "Invalid sort criteria. Expected format is field,direction with field in [date, amount, id] "
          + "and direction in [asc, desc].";

  public static final String AUTHENTICATION_REQUIRED = "Authentication is required to access this resource";
  public static final String ACCESS_DENIED = "You do not have permission to access this resource";
  public static final String INVALID_CREDENTIALS = "Invalid email or password";
  public static final String EMAIL_ALREADY_REGISTERED = "An account already exists for {}";
  public static final String INVALID_REFRESH_TOKEN =
      "The refresh token is invalid, expired or has already been used";

  public static final String VALIDATION_FAILED = "VALIDATION_ERROR";
  public static final String INVALID_REQUEST = "INVALID_REQUEST";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  public static final String UNEXPECTED_ERROR = "An unexpected error occurred";
}
