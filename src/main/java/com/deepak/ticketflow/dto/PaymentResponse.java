package com.deepak.ticketflow.dto;

public class PaymentResponse {
    private boolean success;
    private String paymentId;
    private String errorMessage;

    public static PaymentResponse success(String paymentId) {
        PaymentResponse response = new PaymentResponse();
        response.setSuccess(true);
        response.setPaymentId(paymentId);
        return response;
    }

    public static PaymentResponse failed(String errorMessage) {
        PaymentResponse response = new PaymentResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}