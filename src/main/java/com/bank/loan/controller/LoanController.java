package com.bank.loan.controller;

import com.bank.loan.dto.LoanRequest;
import com.bank.loan.dto.PaymentResult;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanInstallment;
import com.bank.loan.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan management APIs")
public class LoanController {
    private final LoanService loanService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.isOwnCustomer(#request.customerId)")
    @Operation(
            summary = "Create a new loan",
            description = "Create a new loan application. Accessible to ADMIN or the customer owning the account",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan created successfully",
                    content = @Content(schema = @Schema(implementation = Loan.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access")
    })
    public ResponseEntity<Loan> createLoan(@RequestBody LoanRequest request) {
        return ResponseEntity.ok(loanService.createLoan(request));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isOwnCustomer(#customerId)")
    @Operation(
            summary = "Get loans by customer ID",
            description = "Retrieve all loans for a specific customer. Accessible to ADMIN or the customer owning the account",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loans retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Loan.class, type = "array"))),
            @ApiResponse(responseCode = "403", description = "Unauthorized access"),
            @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    public ResponseEntity<List<Loan>> getLoansByCustomer(@PathVariable @NotNull @Parameter(name = "customerId",
            description = "ID of the customer",
            required = true, in = ParameterIn.PATH) Long customerId) {
        return ResponseEntity.ok(loanService.getLoansByCustomerId(customerId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all loans",
            description = "Retrieve all loans (ADMIN only access)",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loans retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Loan.class, type = "array"))),
            @ApiResponse(responseCode = "403", description = "Unauthorized access")
    })
    public ResponseEntity<List<Loan>> getAllLoans() {
        return ResponseEntity.ok(loanService.getAllLoans());
    }

    @PostMapping("/{loanId}/pay")
    @PreAuthorize("hasRole('ADMIN') or @securityService.hasAccessToLoan(#loanId)")
    @Operation(
            summary = "Make loan payment",
            description = "Process a payment for a specific loan. Accessible to ADMIN or authorized users",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment processed successfully",
                    content = @Content(schema = @Schema(implementation = PaymentResult.class))),
            @ApiResponse(responseCode = "400", description = "Invalid amount or loan ID"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access"),
            @ApiResponse(responseCode = "404", description = "Loan not found")
    })
    public ResponseEntity<PaymentResult> payLoan(@PathVariable @NotNull @Parameter(name = "loanId",
                                                         description = "ID of the loan",
                                                         required = true, in = ParameterIn.PATH) Long loanId,
                                                 @RequestParam @PositiveOrZero(message = "Amount must be non-negative")
                                                 @Parameter(name = "amount",
                                                         description = "Payment amount (non-negative)", required = true,
                                                         schema = @Schema(type = "number",
                                                                 format = "bigdecimal", minimum = "0"))
                                                 BigDecimal amount) {
        return ResponseEntity.ok(loanService.processPayment(loanId, amount));
    }

    @GetMapping("/{loanId}/installments")
    @PreAuthorize("hasRole('ADMIN') or @securityService.hasAccessToLoan(#loanId)")
    @Operation(
            summary = "Get loan installments",
            description = "Retrieve all installments for a specific loan. Accessible to ADMIN or authorized users",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Installments retrieved successfully",
                    content = @Content(schema = @Schema(implementation = LoanInstallment.class, type = "array"))),
            @ApiResponse(responseCode = "403", description = "Unauthorized access"),
            @ApiResponse(responseCode = "404", description = "Loan not found")
    })
    public ResponseEntity<List<LoanInstallment>> getInstallments(@PathVariable @NotNull
                                                                 @Parameter(name = "loanId",
                                                                         description = "ID of the loan",
                                                                         required = true,
                                                                         in = ParameterIn.PATH) Long loanId) {
        return ResponseEntity.ok(loanService.getInstallmentsByLoanId(loanId));
    }
}
