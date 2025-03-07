package com.bank.loan.service;

import com.bank.loan.exception.CustomerNotFoundException;
import com.bank.loan.exception.UnauthenticatedUserException;
import com.bank.loan.model.Customer;
import com.bank.loan.repository.CustomerRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityService {
    private final CustomerRepo customerRepo;

    public boolean isAdmin() {
        Authentication authentication = getAuthentication();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));
    }

    public boolean isOwnCustomer(Long customerId) {
        Long currentCustomerId = getCurrentCustomerId();
        return currentCustomerId.equals(customerId);
    }

    public Long getCurrentCustomerId() {
        String username = getCurrentUsername();
        Customer customer = customerRepo.findByUsername(username)
                .orElseThrow(() -> new CustomerNotFoundException(username));
        return customer.getId();
    }

    public String getCurrentUsername() {
        Authentication authentication = getAuthentication();
        return authentication.getName();
    }

    private Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthenticatedUserException();
        }
        return authentication;
    }

    public boolean hasAccessToLoan(Long loanId) {
        if (isAdmin()) return true;
        return customerRepo.existsByLoans_Id(getCurrentCustomerId(), loanId);
    }
}