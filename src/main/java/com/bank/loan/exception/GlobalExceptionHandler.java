package com.bank.loan.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            CustomerNotFoundException.class,
            LoanNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFoundException(RuntimeException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return buildResponse("Resource not found", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({
            CreditLimitExceededException.class,
            InvalidInstallmentException.class,
            InvalidInterestRateException.class
    })
    public ResponseEntity<ErrorResponse> handleBusinessRuleExceptions(RuntimeException ex) {
        log.error("Business rule violation: {}", ex.getMessage());
        return buildResponse("Invalid request", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        log.error("Unauthorized access attempt: {}", ex.getMessage());
        return buildResponse("Access denied", "You don't have permission for this operation", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        return buildResponse("Server error", "Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> buildResponse(String error, String details, HttpStatus status) {
        return new ResponseEntity<>(
                new ErrorResponse(
                        LocalDateTime.now(),
                        status.value(),
                        error,
                        details
                ),
                status
        );
    }

    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String details
    ) {
    }
}