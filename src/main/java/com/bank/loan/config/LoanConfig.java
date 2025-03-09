package com.bank.loan.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "loan")
@Validated
public class LoanConfig {
    @NotNull
    @DecimalMin(value = "0.1", message = "Interest rate must be within the permitted range")
    private BigDecimal interestMin;
    @NotNull
    @DecimalMax(value = "0.5", message = "Interest rate must be within the permitted range")
    private BigDecimal interestMax;
    @NotEmpty
    private List<Integer> installmentsAllowed;
    @DecimalMax(value = "30")
    private int dayOfPayment;
    @DecimalMax(value = "12")
    private int maxAllowedDueMonthCount;
}
