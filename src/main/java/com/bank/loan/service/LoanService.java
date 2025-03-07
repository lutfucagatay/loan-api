package com.bank.loan.service;

import com.bank.loan.config.LoanConfig;
import com.bank.loan.dto.LoanRequest;
import com.bank.loan.dto.PaymentResult;
import com.bank.loan.exception.*;
import com.bank.loan.model.Customer;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanInstallment;
import com.bank.loan.repository.CustomerRepo;
import com.bank.loan.repository.LoanInstallmentRepo;
import com.bank.loan.repository.LoanRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class LoanService {
    private final LoanRepo loanRepo;
    private final CustomerRepo customerRepo;
    private final LoanInstallmentRepo loanInstallmentRepo;
    private final SecurityService securityService;
    private final LoanConfig loanConfig;

    @Transactional
    public Loan createLoan(LoanRequest request) {
        if (!securityService.isAdmin()) {
            request.setCustomerId(securityService.getCurrentCustomerId());
        }

        validateLoanRequest(request);
        Customer customer = getCustomer(request.getCustomerId());

        BigDecimal totalAmount = calculateTotalAmount(request.getAmount(), request.getInterestRate());
        validateCreditLimit(customer, totalAmount);

        Loan loan = buildAndSaveLoan(request, customer, totalAmount);
        createInstallments(loan);
        updateCustomerCreditLimit(customer, totalAmount);

        return loan;
    }

    @Transactional
    public PaymentResult processPayment(Long loanId, BigDecimal paymentAmount) {
        Loan loan = getLoan(loanId);
        validatePaymentAuthorization(loan);

        List<LoanInstallment> installments = getPendingInstallments(loanId);
        PaymentCalculator calculator = new PaymentCalculator(paymentAmount);

        installments.forEach(installment -> {
            if (calculator.hasRemainingFunds()) {
                calculator.processInstallment(installment, LocalDate.now());
            }
        });

        updateLoanStatus(loan);
        return calculator.getResult();
    }

    public List<Loan> getLoansByCustomerId(Long customerId) {
        if (!securityService.isAdmin() && !securityService.isOwnCustomer(customerId)) {
            throw new UnauthorizedAccessException();
        }
        return loanRepo.findByCustomerId(customerId);
    }

    private void validateLoanRequest(LoanRequest request) {
        validateInstallments(request.getInstallments());
        validateInterestRate(request.getInterestRate());
        validateCustomerExists(request.getCustomerId());
    }

    private void validateCustomerExists(Long customerId) {
        if (!customerRepo.existsById(customerId)) {
            throw new CustomerNotFoundException(String.valueOf(customerId));
        }
    }

    private void validateInstallments(Integer installments) {
        if (!loanConfig.getInstallmentsAllowed().contains(installments)) {
            throw new InvalidInstallmentException(
                    "Allowed installments: " + loanConfig.getInstallmentsAllowed()
            );
        }
    }

    private void validateInterestRate(BigDecimal rate) {
        if (rate.compareTo(loanConfig.getInterestMin()) < 0 ||
                rate.compareTo(loanConfig.getInterestMax()) > 0) {
            throw new InvalidInterestRateException(
                    String.format("Interest rate must be between %s and %s",
                            loanConfig.getInterestMin(),
                            loanConfig.getInterestMax())
            );
        }
    }

    private Customer getCustomer(Long customerId) {
        return customerRepo.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId.toString()));
    }

    private Loan getLoan(Long loanId) {
        return loanRepo.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));
    }

    private BigDecimal calculateTotalAmount(BigDecimal principal, BigDecimal interestRate) {
        return principal.multiply(BigDecimal.ONE.add(interestRate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateCreditLimit(Customer customer, BigDecimal loanAmount) {
        BigDecimal newUsedCredit = customer.getUsedCreditLimit().add(loanAmount);
        if (newUsedCredit.compareTo(customer.getCreditLimit()) > 0) {
            throw new CreditLimitExceededException("Credit limit exceeded.");
        }
    }

    private Loan buildAndSaveLoan(LoanRequest request, Customer customer, BigDecimal totalAmount) {
        return loanRepo.save(Loan.builder()
                .customer(customer)
                .totalAmount(totalAmount)
                .numberOfInstallments(request.getInstallments())
                .createDate(LocalDate.now())
                .isPaid(false)
                .build());
    }

    private void createInstallments(Loan loan) {
        List<LoanInstallment> installments = new ArrayList<>();
        BigDecimal installmentAmount = loan.getTotalAmount()
                .divide(BigDecimal.valueOf(loan.getNumberOfInstallments()), 2, RoundingMode.HALF_UP);

        LocalDate dueDate = LocalDate.now()
                .plusMonths(1)
                .withDayOfMonth(1);

        IntStream.range(0, loan.getNumberOfInstallments()).forEachOrdered(installment -> installments.add(LoanInstallment.builder()
                .loan(loan)
                .amount(installmentAmount)
                .dueDate(dueDate.plusMonths(installment))
                .isPaid(false)
                .paidAmount(BigDecimal.ZERO)
                .build()));

        loanInstallmentRepo.saveAll(installments);
    }

    private void updateCustomerCreditLimit(Customer customer, BigDecimal loanAmount) {
        customer.setUsedCreditLimit(customer.getUsedCreditLimit().add(loanAmount));
        customerRepo.save(customer);
    }

    private List<LoanInstallment> getPendingInstallments(Long loanId) {
        return loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loanId).stream()
                .filter(installment -> !installment.getIsPaid())
                .filter(this::isWithinPaymentWindow)
                .collect(Collectors.toList());
    }

    private boolean isWithinPaymentWindow(LoanInstallment installment) {
        YearMonth currentYearMonth = YearMonth.from(LocalDate.now());
        YearMonth cutoffYearMonth = currentYearMonth.plusMonths(3);//TODO put to config
        LocalDate cutoffDate = cutoffYearMonth.atDay(1); // First day of the 4th month //TODO put to config
        return installment.getDueDate().isBefore(cutoffDate);
    }

    private void validatePaymentAuthorization(Loan loan) {
        if (!securityService.isAdmin() &&
                !securityService.isOwnCustomer(loan.getCustomer().getId())) {
            throw new UnauthorizedPaymentException();
        }
    }

    private void updateLoanStatus(Loan loan) {
        List<LoanInstallment> installments = loanInstallmentRepo.findByLoanId(loan.getId());
        boolean allPaid = installments.stream().allMatch(LoanInstallment::getIsPaid);

        if (allPaid) {
            loan.setIsPaid(true);
            Customer customer = loan.getCustomer();
            customer.setUsedCreditLimit(
                    customer.getUsedCreditLimit().subtract(loan.getTotalAmount())
            );
            customerRepo.save(customer);
            loanRepo.save(loan);
        }
    }

    public List<Loan> getAllLoans() {
        if (!securityService.isAdmin()) {
            throw new UnauthorizedAccessException();
        }
        return loanRepo.findAll();
    }

    public List<LoanInstallment> getInstallmentsByLoanId(Long loanId) {
        return loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loanId);
    }

    // Payment calculation helper
    private static class PaymentCalculator {
        private BigDecimal remainingFunds;
        private int paidInstallments = 0;
        private BigDecimal totalPaid = BigDecimal.ZERO;

        PaymentCalculator(BigDecimal paymentAmount) {
            this.remainingFunds = paymentAmount;
        }

        boolean hasRemainingFunds() {
            return remainingFunds.compareTo(BigDecimal.ZERO) > 0;
        }

        void processInstallment(LoanInstallment installment, LocalDate paymentDate) {
            BigDecimal requiredPayment = calculateEffectivePayment(installment, paymentDate);

            if (remainingFunds.compareTo(requiredPayment) >= 0) {
                remainingFunds = remainingFunds.subtract(requiredPayment);
                totalPaid = totalPaid.add(requiredPayment);
                paidInstallments++;

                installment.setPaidAmount(requiredPayment);
                installment.setPaymentDate(paymentDate);
                installment.setIsPaid(true);
            }
        }

        private BigDecimal calculateEffectivePayment(LoanInstallment installment, LocalDate paymentDate) {
            long daysDifference = ChronoUnit.DAYS.between(paymentDate, installment.getDueDate());
            BigDecimal adjustment = installment.getAmount()
                    .multiply(BigDecimal.valueOf(0.001))
                    .multiply(BigDecimal.valueOf(Math.abs(daysDifference)));

            return daysDifference > 0
                    ? installment.getAmount().subtract(adjustment)
                    : installment.getAmount().add(adjustment);
        }

        PaymentResult getResult() {
            return new PaymentResult(
                    paidInstallments,
                    totalPaid,
                    remainingFunds.equals(BigDecimal.ZERO)
            );
        }
    }
}