package com.bank.loan.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String message) {
        super("Customer not found : " + message);
    }
}
