package com.deepak.ticketflow.handlers;

public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String msg) { super(msg); }
}