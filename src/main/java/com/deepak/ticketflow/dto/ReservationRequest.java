package com.deepak.ticketflow.dto;

import java.util.List;

public class ReservationRequest {
    private Long eventId;
    private List<String> seatNumbers;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public List<String> getSeatNumbers() {
        return seatNumbers;
    }

    public void setSeatNumbers(List<String> seatNumbers) {
        this.seatNumbers = seatNumbers;
    }
}