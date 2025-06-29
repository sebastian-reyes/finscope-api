package com.sreyes.finscope.exception.custom;

public class TransactionTypeNotFoundException extends RuntimeException {
    public TransactionTypeNotFoundException(String message) {
        super(message);
    }
}
