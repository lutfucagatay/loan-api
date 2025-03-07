package com.bank.loan.config;

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
        // ADMIN user (can manage all)
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin"))
                .roles("ADMIN")
                .build();

        // CUSTOMER user (linked to a Customer entity)
        UserDetails customer1 = User.builder()
                .username("john") // Matches Customer.username in DB
                .password(passwordEncoder().encode("password"))
                .roles("CUSTOMER")
                .build();

        return new InMemoryUserDetailsManager(admin, customer1);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
