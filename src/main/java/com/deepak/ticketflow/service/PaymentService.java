package com.deepak.ticketflow.service;

import com.deepak.ticketflow.dto.PaymentRequest;
import com.deepak.ticketflow.dto.PaymentResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        return PaymentResponse.success("DUMMY-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}