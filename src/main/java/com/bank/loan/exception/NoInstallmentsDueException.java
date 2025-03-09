package com.bank.loan.exception;


public class NoInstallmentsDueException extends RuntimeException {
    public NoInstallmentsDueException(String message) {
        super(message);
    }
}