package com.deepak.ticketflow.handlers;

public class InvalidSeatStateException extends RuntimeException {
    public InvalidSeatStateException(String message) {
        super(message);
    }
}