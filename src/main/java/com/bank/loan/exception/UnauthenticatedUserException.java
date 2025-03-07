package com.bank.loan.exception;

public class UnauthenticatedUserException extends RuntimeException {
    public UnauthenticatedUserException() {
        super("Unauthenticated user");
    }
}
