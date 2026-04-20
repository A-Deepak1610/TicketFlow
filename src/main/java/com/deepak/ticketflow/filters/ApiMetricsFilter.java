package com.deepak.ticketflow.filters;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiMetricsFilter extends OncePerRequestFilter {

    private final Counter apiRequestsCounter;
    private final Counter apiFailuresCounter;

    public ApiMetricsFilter(MeterRegistry meterRegistry) {
        this.apiRequestsCounter = meterRegistry.counter("api_requests");
        this.apiFailuresCounter = meterRegistry.counter("api_failures");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        apiRequestsCounter.increment();

        try {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 400) {
                apiFailuresCounter.increment();
            }
        } catch (Exception ex) {
            apiFailuresCounter.increment();
            throw ex;
        }
    }
}