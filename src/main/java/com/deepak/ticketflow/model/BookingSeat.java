package com.deepak.ticketflow.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_seats")
public class BookingSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingSeatId;
    private Long bookingId;
    private Long seatId;
    @Column(precision = 10, scale = 2) private BigDecimal price;
    // getters & setters

    public Long getBookingSeatId() {
        return bookingSeatId;
    }

    public void setBookingSeatId(Long bookingSeatId) {
        this.bookingSeatId = bookingSeatId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}