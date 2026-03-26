package com.deepak.ticketflow.handlers;

public class SeatLockException extends RuntimeException {
	public SeatLockException(String message) {
		super(message);
	}
}
