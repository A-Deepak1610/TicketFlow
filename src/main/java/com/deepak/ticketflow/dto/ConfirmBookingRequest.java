package com.deepak.ticketflow.dto;

import java.util.List;

public class ConfirmBookingRequest {
    private List<Long> reservationIds;
    private PaymentRequest paymentRequest;

    public List<Long> getReservationIds() {
        return reservationIds;
    }

    public void setReservationIds(List<Long> reservationIds) {
        this.reservationIds = reservationIds;
    }

    public PaymentRequest getPaymentRequest() {
        return paymentRequest;
    }

    public void setPaymentRequest(PaymentRequest paymentRequest) {
        this.paymentRequest = paymentRequest;
    }
}