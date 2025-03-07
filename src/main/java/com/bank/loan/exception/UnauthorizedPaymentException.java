package com.bank.loan.exception;

public class UnauthorizedPaymentException extends RuntimeException {
    public UnauthorizedPaymentException() {
        super("Unauthorized payment");
    }
}
