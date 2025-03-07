package com.bank.loan.exception;

public class InvalidInstallmentException extends RuntimeException {
    public InvalidInstallmentException(String message) {
        super(message);
    }
}
