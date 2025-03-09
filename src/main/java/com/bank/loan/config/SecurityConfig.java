package com.bank.loan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${ADMIN_USERNAME}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    @Value("${CUSTOMER_USERNAME}")
    private String customerUsername;

    @Value("${CUSTOMER_PASSWORD}")
    private String customerPassword;


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // ADMIN-only endpoints (customer management)
                        .requestMatchers(HttpMethod.POST, "/customers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/customers/**").hasRole("ADMIN")

                        // Authenticated endpoints (ADMIN or CUSTOMER)
                        .requestMatchers(HttpMethod.POST, "/loans").authenticated()
                        .requestMatchers(HttpMethod.GET, "/loans/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/loans/*/pay").authenticated()
                        .requestMatchers(HttpMethod.GET, "/loans/*/installments").authenticated()

                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder().encode(adminPassword))
                .roles("ADMIN")
                .build();

        UserDetails customer1 = User.builder()
                .username(customerUsername) // Matches Customer.username in DB
                .password(passwordEncoder().encode(customerPassword))
                .roles("CUSTOMER")
                .build();

        return new InMemoryUserDetailsManager(admin, customer1);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
