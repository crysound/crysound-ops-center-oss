package com.crysound.opscenter.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService users(SecurityProperties properties, PasswordEncoder encoder) {
        properties.validate();
        if (properties.operatorUsername().equalsIgnoreCase(properties.viewerUsername())) {
            throw new IllegalArgumentException("Operator and viewer identities must be distinct");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(properties.operatorUsername()).password(encoder.encode(properties.operatorPassword()))
                        .roles("OPERATOR").build(),
                User.withUsername(properties.viewerUsername()).password(encoder.encode(properties.viewerPassword()))
                        .roles("VIEWER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'none'; style-src 'self'; img-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/login", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/tasks/*/run", "/api/tasks/*/runs").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.GET, "/", "/api/**").hasAnyRole("OPERATOR", "VIEWER")
                .anyRequest().denyAll());
        http.formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/", true).permitAll());
        http.logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }
}
