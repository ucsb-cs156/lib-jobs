package edu.ucsb.cs156.jobs.testapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stands in for the app-level security config the real apps already have: enables method security
 * so the controller's {@code @PreAuthorize} rules are enforced, and otherwise stays out of the way
 * (permit-all chain, no CSRF) so tests exercise exactly the method-security behavior.
 */
@Configuration
@EnableMethodSecurity
public class TestSecurityConfig {
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }
}
