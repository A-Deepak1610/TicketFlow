package com.deepak.ticketflow.dto;

import com.deepak.ticketflow.model.Reservation;

import java.time.LocalDateTime;
import java.util.List;

public class ReservationResponse {
    private List<Long> reservationIds;
    private LocalDateTime reservedUntil;

    public ReservationResponse(List<Reservation> reservations, LocalDateTime reservedUntil) {
        this.reservationIds = reservations.stream().map(Reservation::getReservationId).toList();
        this.reservedUntil = reservedUntil;
    }

    public List<Long> getReservationIds() {
        return reservationIds;
    }

    public void setReservationIds(List<Long> reservationIds) {
        this.reservationIds = reservationIds;
    }

    public LocalDateTime getReservedUntil() {
        return reservedUntil;
    }

    public void setReservedUntil(LocalDateTime reservedUntil) {
        this.reservedUntil = reservedUntil;
    }
}