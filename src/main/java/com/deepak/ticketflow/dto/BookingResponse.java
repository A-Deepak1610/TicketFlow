package com.deepak.ticketflow.dto;

import com.deepak.ticketflow.model.Booking;

public class BookingResponse {
    private Long bookingId;
    private String bookingReference;
    private String paymentId;
    private String paymentStatus;

    public BookingResponse(Booking booking) {
        this.bookingId = booking.getBookingId();
        this.bookingReference = booking.getBookingReference();
        this.paymentId = booking.getPaymentId();
        this.paymentStatus = booking.getPaymentStatus();
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}