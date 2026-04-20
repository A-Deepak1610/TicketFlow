package com.deepak.ticketflow.filters;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MetricsFilter extends OncePerRequestFilter {

    @Autowired
    private MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IndexOutOfBoundsException, IOException {

        try {
            filterChain.doFilter(request, response);

            String status = response.getStatus() < 400 ? "success" : "failure";

            meterRegistry.counter(
                    "api_requests",
                    "status", status,
                    "method", request.getMethod(),
                    "endpoint", request.getRequestURI()
            ).increment();

        } catch (Exception ex) {

            meterRegistry.counter(
                    "api_requests",
                    "status", "failure",
                    "method", request.getMethod(),
                    "endpoint", request.getRequestURI()
            ).increment();

            throw ex;
        }
    }
}