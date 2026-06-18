package com.deepak.ticketflow.config;
import com.deepak.ticketflow.filters.ApiMetricsFilter;
import com.deepak.ticketflow.filters.JwtFilter;

import com.deepak.ticketflow.handlers.CustomAccessDeniedHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SpringConfig {
    @Autowired
    private AuthenticationProvider customAuthProvider;
    @Autowired
    JwtFilter  jwtFilter;
    @Autowired
    ApiMetricsFilter apiMetricsFilter;
    @Autowired
    CustomAccessDeniedHandler customAccessDeniedHandler;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf->csrf.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/login", "/register","/refresh","/events","/events/*","/actuator/prometheus","/swagger-ui.html","/swagger-ui/index.html","/v3/api-docs","/").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(customAuthProvider)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .build();
    }
    @Bean//Create this object and manage it so that it can be injected anywhere using @Autowired
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}
