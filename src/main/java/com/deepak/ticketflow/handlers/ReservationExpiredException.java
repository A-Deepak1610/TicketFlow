package com.deepak.ticketflow.handlers;

public class ReservationExpiredException extends RuntimeException {
    public ReservationExpiredException(String msg) { super(msg); }
}