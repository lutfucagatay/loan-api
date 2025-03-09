package com.bank.loan.service;

import com.bank.loan.config.LoanConfig;
import com.bank.loan.dto.LoanRequest;
import com.bank.loan.dto.PaymentResult;
import com.bank.loan.exception.*;
import com.bank.loan.helper.PaymentCalculator;
import com.bank.loan.model.Customer;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanInstallment;
import com.bank.loan.repository.CustomerRepo;
import com.bank.loan.repository.LoanInstallmentRepo;
import com.bank.loan.repository.LoanRepo;
import com.bank.loan.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepo loanRepo;
    @Mock
    private CustomerRepo customerRepo;
    @Mock
    private LoanInstallmentRepo loanInstallmentRepo;
    @Mock
    private SecurityService securityService;
    @Mock
    private LoanConfig loanConfig;
    @Mock
    private PaymentCalculator paymentCalculator;

    @InjectMocks
    private LoanService loanService;

    private Customer customer;
    private Loan loan;
    private LoanRequest loanRequest;

    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .id(1L)
                .creditLimit(BigDecimal.valueOf(10000))
                .usedCreditLimit(BigDecimal.valueOf(2000))
                .build();

        loanRequest = LoanRequest.builder()
                .customerId(1L)
                .amount(BigDecimal.valueOf(1000))
                .interestRate(BigDecimal.valueOf(0.05))
                .installments(12)
                .build();

        loan = Loan.builder()
                .id(100L)
                .customer(customer)
                .loanAmount(BigDecimal.valueOf(1050))
                .numberOfInstallments(12)
                .createDate(LocalDate.now())
                .isPaid(false)
                .build();
    }

    @Test
    void createLoan_success_asAdmin() {
        when(securityService.isAdmin()).thenReturn(true);
        when(customerRepo.existsById(loanRequest.getCustomerId())).thenReturn(true);
        when(customerRepo.findById(loanRequest.getCustomerId())).thenReturn(Optional.of(customer));
        when(loanConfig.getInstallmentsAllowed()).thenReturn(List.of(12));
        when(loanConfig.getInterestMin()).thenReturn(BigDecimal.valueOf(0.01));
        when(loanConfig.getInterestMax()).thenReturn(BigDecimal.valueOf(0.10));
        when(loanRepo.save(any(Loan.class))).thenReturn(loan);

        Loan createdLoan = loanService.createLoan(loanRequest);

        assertNotNull(createdLoan);
        assertEquals(loan.getId(), createdLoan.getId());
        verify(loanRepo, times(1)).save(any(Loan.class));
        verify(customerRepo, times(1)).save(customer);
        assertEquals(BigDecimal.valueOf(3050.00).setScale(2, RoundingMode.HALF_UP), customer.getUsedCreditLimit());
    }

    @Test
    void createLoan_success_asNonAdmin() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.getCurrentCustomerId()).thenReturn(1L);
        loanRequest.setCustomerId(2L); //Different customer id to check override
        when(customerRepo.existsById(1L)).thenReturn(true);
        when(customerRepo.findById(1L)).thenReturn(Optional.of(customer));
        when(loanConfig.getInstallmentsAllowed()).thenReturn(List.of(12));
        when(loanConfig.getInterestMin()).thenReturn(BigDecimal.valueOf(0.01));
        when(loanConfig.getInterestMax()).thenReturn(BigDecimal.valueOf(0.10));
        when(loanRepo.save(any(Loan.class))).thenReturn(loan);

        Loan createdLoan = loanService.createLoan(loanRequest);

        assertNotNull(createdLoan);
        assertEquals(loan.getId(), createdLoan.getId());
        verify(loanRepo, times(1)).save(any(Loan.class));
        verify(customerRepo, times(1)).save(customer);
        assertEquals(BigDecimal.valueOf(3050.00).setScale(2, RoundingMode.HALF_UP), customer.getUsedCreditLimit());
        assertEquals(1L, loanRequest.getCustomerId());
    }

    @Test
    void createLoan_customerNotFound() {
        when(securityService.isAdmin()).thenReturn(true);
        when(customerRepo.existsById(loanRequest.getCustomerId())).thenReturn(false);
        when(customerRepo.findById(loanRequest.getCustomerId())).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> loanService.createLoan(loanRequest));

        verify(loanRepo, never()).save(any(Loan.class));
        verify(customerRepo, never()).save(customer);
    }

    @Test
    void createLoan_invalidInstallment() {
        when(securityService.isAdmin()).thenReturn(true);
        when(customerRepo.existsById(loanRequest.getCustomerId())).thenReturn(true);
        when(customerRepo.findById(loanRequest.getCustomerId())).thenReturn(Optional.of(customer));
        when(loanConfig.getInstallmentsAllowed()).thenReturn(List.of(6, 9, 12, 24));
        when(loanConfig.getInterestMin()).thenReturn(BigDecimal.valueOf(0.01));
        when(loanConfig.getInterestMax()).thenReturn(BigDecimal.valueOf(0.10));

        assertThrows(InvalidInstallmentException.class, () -> loanService.createLoan(loanRequest));

        verify(loanRepo, never()).save(any(Loan.class));
        verify(customerRepo, never()).save(customer);
    }

    @Test
    void createLoan_invalidInterestRate() {
        when(securityService.isAdmin()).thenReturn(true);
//        when(customerRepo.existsById(loanRequest.getCustomerId())).thenReturn(true);
        when(loanConfig.getInstallmentsAllowed()).thenReturn(List.of(12));
        when(loanConfig.getInterestMin()).thenReturn(BigDecimal.valueOf(0.06));
        when(loanConfig.getInterestMax()).thenReturn(BigDecimal.valueOf(0.10));
        loanRequest.setInterestRate(BigDecimal.valueOf(0.05));

        assertThrows(InvalidInterestRateException.class, () -> loanService.createLoan(loanRequest));

        verify(loanRepo, never()).save(any(Loan.class));
        verify(customerRepo, never()).save(customer);
    }

    @Test
    void createLoan_creditLimitExceeded() {
        when(securityService.isAdmin()).thenReturn(true);
        when(customerRepo.existsById(loanRequest.getCustomerId())).thenReturn(true);
        when(customerRepo.findById(loanRequest.getCustomerId())).thenReturn(Optional.of(customer));
        when(loanConfig.getInstallmentsAllowed()).thenReturn(List.of(12));
        when(loanConfig.getInterestMin()).thenReturn(BigDecimal.valueOf(0.01));
        when(loanConfig.getInterestMax()).thenReturn(BigDecimal.valueOf(0.10));
        customer.setCreditLimit(BigDecimal.valueOf(2000));

        assertThrows(CreditLimitExceededException.class, () -> loanService.createLoan(loanRequest));

        verify(loanRepo, never()).save(any(Loan.class));
        verify(customerRepo, never()).save(customer);
    }

    @Test
    void processPayment_success() {
        BigDecimal paymentAmount = BigDecimal.valueOf(100);
        LoanInstallment installment = LoanInstallment.builder()
                .id(1L)
                .loan(loan)
                .amount(BigDecimal.valueOf(87.5))
                .dueDate(LocalDate.now().plusMonths(1))
                .isPaid(false)
                .paidAmount(BigDecimal.ZERO)
                .build();
        List<LoanInstallment> installments = new ArrayList<>();
        installments.add(installment);

        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));
        when(securityService.isAdmin()).thenReturn(true);
        when(loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loan.getId())).thenReturn(installments);
        when(paymentCalculator.getResult()).thenReturn(new PaymentResult(1, paymentAmount, false, BigDecimal.ZERO));
        when(loanConfig.getDayOfPayment()).thenReturn(1);

        PaymentResult result = loanService.processPayment(loan.getId(), paymentAmount);

        assertNotNull(result);
        verify(loanInstallmentRepo, times(1)).saveAll(installments);
        verify(loanRepo, never()).save(loan); //Loan is not fully paid
    }

    @Test
    void processPayment_noInstallmentsDue() {
        BigDecimal paymentAmount = BigDecimal.valueOf(100);
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));
        when(securityService.isAdmin()).thenReturn(true);
        when(loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loan.getId())).thenReturn(new ArrayList<>());

        assertThrows(NoInstallmentsDueException.class, () -> loanService.processPayment(loan.getId(), paymentAmount));

        verify(loanInstallmentRepo, never()).saveAll(any());
        verify(loanRepo, never()).save(loan);
    }

    @Test
    void processPayment_loanNotFound() {
        BigDecimal paymentAmount = BigDecimal.valueOf(100);
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.empty());

        assertThrows(LoanNotFoundException.class, () -> loanService.processPayment(loan.getId(), paymentAmount));

        verify(loanInstallmentRepo, never()).saveAll(any());
        verify(loanRepo, never()).save(loan);
    }

    @Test
    void getLoansByCustomerId_success_asAdmin() {
        when(securityService.isAdmin()).thenReturn(true);
        when(loanRepo.findByCustomerId(customer.getId())).thenReturn(List.of(loan));

        List<Loan> loans = loanService.getLoansByCustomerId(customer.getId());

        assertNotNull(loans);
        assertEquals(1, loans.size());
        assertEquals(loan.getId(), loans.get(0).getId());
    }

    @Test
    void getLoansByCustomerId_success_asOwnCustomer() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isOwnCustomer(customer.getId())).thenReturn(true);
        when(loanRepo.findByCustomerId(customer.getId())).thenReturn(List.of(loan));

        List<Loan> loans = loanService.getLoansByCustomerId(customer.getId());

        assertNotNull(loans);
        assertEquals(1, loans.size());
        assertEquals(loan.getId(), loans.get(0).getId());
    }

    @Test
    void getLoansByCustomerId_unauthorized() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isOwnCustomer(customer.getId())).thenReturn(false);

        assertThrows(UnauthorizedAccessException.class, () -> loanService.getLoansByCustomerId(customer.getId()));
    }

    @Test
    void processPayment_fullSingleInstallmentPayment() {
        // Setup
        LoanInstallment installment = LoanInstallment.builder()
                .id(1L)
                .loan(loan)
                .amount(BigDecimal.valueOf(87.50))
                .dueDate(LocalDate.now().plusDays(1))
                .isPaid(false)
                .paidAmount(BigDecimal.ZERO)
                .build();
        List<LoanInstallment> installments = List.of(installment);

        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));
        when(securityService.isAdmin()).thenReturn(true);
        when(loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loan.getId())).thenReturn(installments);
        when(paymentCalculator.getResult()).thenReturn(new PaymentResult(1, BigDecimal.valueOf(87.50), false, BigDecimal.valueOf(0)));
        when(loanConfig.getDayOfPayment()).thenReturn(1);

        // Test
        PaymentResult result = loanService.processPayment(loan.getId(), BigDecimal.valueOf(87.50));

        // Verify
        assertAll(
                () -> assertEquals(1, result.paidInstallments()),
                () -> assertEquals(BigDecimal.valueOf(87.50), result.totalPaid()),
                () -> assertEquals(BigDecimal.valueOf(0), result.remainingFunds()),
                () -> assertFalse(result.isLoanPaid()), // Not last installment
                () -> assertFalse(installment.getIsPaid())
        );
    }

    @Test
    void processPayment_overpaymentWithRemainingFunds() {
        // Setup
        LoanInstallment installment = LoanInstallment.builder()
                .id(1L)
                .loan(loan)
                .amount(BigDecimal.valueOf(50.00))
                .dueDate(LocalDate.now().plusDays(1))
                .isPaid(false)
                .paidAmount(BigDecimal.ZERO)
                .build();
        List<LoanInstallment> installments = List.of(installment);

        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));
        when(securityService.isAdmin()).thenReturn(true);
        when(loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loan.getId())).thenReturn(installments);
        when(paymentCalculator.getResult()).thenReturn(new PaymentResult(1, BigDecimal.valueOf(50.00), false, BigDecimal.valueOf(25.00)));
        when(loanConfig.getDayOfPayment()).thenReturn(1);
        // Test
        PaymentResult result = loanService.processPayment(loan.getId(), BigDecimal.valueOf(75.00));

        // Verify
        assertAll(
                () -> assertEquals(1, result.paidInstallments()),
                () -> assertEquals(BigDecimal.valueOf(50.00), result.totalPaid()),
                () -> assertEquals(BigDecimal.valueOf(25.00), result.remainingFunds()),
                () -> assertFalse(installment.getIsPaid())
        );
    }

    @Test
    void processPayment_fullLoanPayment() {
        // Setup final installment
        loan.setNumberOfInstallments(1);
        LoanInstallment installment = LoanInstallment.builder()
                .id(1L)
                .loan(loan)
                .amount(loan.getLoanAmount())
                .dueDate(LocalDate.now().plusDays(1))
                .isPaid(false)
                .paidAmount(BigDecimal.ZERO)
                .build();
        List<LoanInstallment> installments = List.of(installment);

        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));
        when(securityService.isAdmin()).thenReturn(true);
        when(loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loan.getId())).thenReturn(installments);
        when(paymentCalculator.getResult()).thenReturn(new PaymentResult(1, loan.getLoanAmount(), true, BigDecimal.valueOf(0)));
        when(loanConfig.getDayOfPayment()).thenReturn(1);

        // Test
        PaymentResult result = loanService.processPayment(loan.getId(), loan.getLoanAmount());

        // Verify
        assertAll(
                () -> assertEquals(1, result.paidInstallments()),
                () -> assertEquals(loan.getLoanAmount(), result.totalPaid()),
                () -> assertEquals(BigDecimal.valueOf(0), result.remainingFunds()),
                () -> assertTrue(result.isLoanPaid()),
                () -> assertFalse(loan.isPaid())//Check only result  because updateLoanStatus not tested
        );
    }

    @Test
    void processPayment_partialInstallmentPayment() {
        // Setup
        LoanInstallment installment = LoanInstallment.builder()
                .id(1L)
                .loan(loan)
                .amount(BigDecimal.valueOf(100.00))
                .dueDate(LocalDate.now().plusDays(1))
                .isPaid(false)
                .paidAmount(BigDecimal.ZERO)
                .build();
        List<LoanInstallment> installments = List.of(installment);

        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));
        when(securityService.isAdmin()).thenReturn(true);
        when(loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loan.getId())).thenReturn(installments);
        when(paymentCalculator.getResult()).thenReturn(new PaymentResult(0, BigDecimal.valueOf(30.00), false, BigDecimal.valueOf(0)));
        when(loanConfig.getDayOfPayment()).thenReturn(1);

        // Test
        PaymentResult result = loanService.processPayment(loan.getId(), BigDecimal.valueOf(30.00));

        // Verify
        assertAll(
                () -> assertEquals(0, result.paidInstallments()),
                () -> assertEquals(BigDecimal.valueOf(30.00), result.totalPaid()),
                () -> assertEquals(BigDecimal.valueOf(0), result.remainingFunds()),
                () -> assertFalse(result.isLoanPaid()),
                () -> assertEquals(BigDecimal.ZERO, installment.getPaidAmount()),
                () -> assertFalse(installment.getIsPaid())
        );
    }
}
