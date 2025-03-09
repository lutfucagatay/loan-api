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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

    @Captor
    private ArgumentCaptor<List<LoanInstallment>> installmentCaptor;

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
        assertEquals(BigDecimal.valueOf(3050).setScale(2, RoundingMode.HALF_UP), customer.getUsedCreditLimit());
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
        assertEquals(BigDecimal.valueOf(3050).setScale(2, RoundingMode.HALF_UP), customer.getUsedCreditLimit());
        assertEquals(1L, loanRequest.getCustomerId());
    }

    @Test
    void createLoan_customerNotFound() {
        when(securityService.isAdmin()).thenReturn(true);
        when(customerRepo.existsById(loanRequest.getCustomerId())).thenReturn(false);

        assertThrows(CustomerNotFoundException.class, () -> loanService.createLoan(loanRequest));

        verify(loanRepo, never()).save(any(Loan.class));
        verify(customerRepo, never()).save(customer);
    }

    @Test
    void createLoan_invalidInstallment() {
        when(securityService.isAdmin()).thenReturn(true);
        when(customerRepo.existsById(loanRequest.getCustomerId())).thenReturn(true);
        when(loanConfig.getInstallmentsAllowed()).thenReturn(List.of(6, 24));

        assertThrows(InvalidInstallmentException.class, () -> loanService.createLoan(loanRequest));

        verify(loanRepo, never()).save(any(Loan.class));
        verify(customerRepo, never()).save(customer);
    }

    @Test
    void createLoan_invalidInterestRate() {
        when(securityService.isAdmin()).thenReturn(true);
        when(customerRepo.existsById(loanRequest.getCustomerId())).thenReturn(true);
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
        when(loanConfig.getMaxAllowedDueMonthCount()).thenReturn(3);
        when(loanConfig.getDayOfPayment()).thenReturn(1);
//        doAnswer(invocation -> {
//            LoanInstallment inst = invocation.getArgument(0);
//            LocalDate date = invocation.getArgument(1);
//            return null;
//        }).when(paymentCalculator).processInstallment(any(LoanInstallment.class), any(LocalDate.class));

        PaymentResult result = loanService.processPayment(loan.getId(), paymentAmount);

        assertNotNull(result);
        verify(loanInstallmentRepo, times(1)).saveAll(any());
//        verify(paymentCalculator, times(1)).processInstallment(any(), any()); todo fix
//        verify(loanRepo, never()).save(any());
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
        verify(paymentCalculator, never()).processInstallment(any(), any());
    }

    @Test
    void processPayment_loanNotFound() {
        BigDecimal paymentAmount = BigDecimal.valueOf(100);
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.empty());

        assertThrows(LoanNotFoundException.class, () -> loanService.processPayment(loan.getId(), paymentAmount));

        verify(loanInstallmentRepo, never()).saveAll(any());
        verify(loanRepo, never()).save(loan);
        verify(paymentCalculator, never()).processInstallment(any(), any());
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
    void getInstallmentsByLoanId_success() {
        List<LoanInstallment> installments = new ArrayList<>();
        installments.add(LoanInstallment.builder().id(1L).loan(loan).amount(BigDecimal.TEN).dueDate(LocalDate.now()).isPaid(false).build());

        when(loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loan.getId())).thenReturn(installments);
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        List<LoanInstallment> result = loanService.getInstallmentsByLoanId(loan.getId());

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void getInstallmentsByLoanId_loanNotFound() {
        assertThrows(LoanNotFoundException.class, () -> loanService.getInstallmentsByLoanId(loan.getId()));
    }

    @Test
    void getAllLoans_success() {
        List<Loan> loans = new ArrayList<>();
        loans.add(loan);

        when(securityService.isAdmin()).thenReturn(true);
        when(loanRepo.findAll()).thenReturn(loans);

        List<Loan> result = loanService.getAllLoans();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(loan.getId(), result.get(0).getId());
    }

    @Test
    void getAllLoans_unauthorized() {
        when(securityService.isAdmin()).thenReturn(false);

        assertThrows(UnauthorizedAccessException.class, () -> loanService.getAllLoans());
    }

    @Test
    void createInstallments_createsInstallmentsCorrectly() {
        Loan loan = Loan.builder()
                .id(1L)
                .loanAmount(BigDecimal.valueOf(1200))
                .numberOfInstallments(3)
                .createDate(LocalDate.now())
                .build();

        loanService.createInstallments(loan);

        verify(loanInstallmentRepo).saveAll(installmentCaptor.capture());
        List<LoanInstallment> capturedInstallments = installmentCaptor.getValue();

        assertEquals(3, capturedInstallments.size());
        assertEquals(BigDecimal.valueOf(400).setScale(2, RoundingMode.HALF_UP), capturedInstallments.get(0).getAmount());
        assertEquals(BigDecimal.valueOf(400).setScale(2, RoundingMode.HALF_UP), capturedInstallments.get(1).getAmount());
        assertEquals(BigDecimal.valueOf(400).setScale(2, RoundingMode.HALF_UP), capturedInstallments.get(2).getAmount());
        assertEquals(LocalDate.now().plusMonths(1).withDayOfMonth(1), capturedInstallments.get(0).getDueDate());
        assertEquals(LocalDate.now().plusMonths(2).withDayOfMonth(1), capturedInstallments.get(1).getDueDate());
        assertEquals(LocalDate.now().plusMonths(3).withDayOfMonth(1), capturedInstallments.get(2).getDueDate());
    }
}
