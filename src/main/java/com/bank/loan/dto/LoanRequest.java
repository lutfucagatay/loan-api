package com.bank.loan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@Validated
public class LoanRequest {
    @NotNull
    private Long customerId;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private Integer installments;
}
