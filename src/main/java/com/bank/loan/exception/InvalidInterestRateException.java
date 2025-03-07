package com.bank.loan.exception;

public class InvalidInterestRateException extends RuntimeException {
    public InvalidInterestRateException(String message) {
        super(message);
    }
}
