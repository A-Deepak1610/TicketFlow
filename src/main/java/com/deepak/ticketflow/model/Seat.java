package com.deepak.ticketflow.model;

import com.deepak.ticketflow.Enum.SeatStatus;
import com.deepak.ticketflow.Enum.SeatType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seats")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "seat_number", length = 20)
    private String seatNumber;
    @Column(name = "section", length = 50)
    private String section;
    @Column(name = "row_no", length = 10)
    private String rowNo;
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type")
    private SeatType seatType;
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SeatStatus status;
    @Version
    @Column(name = "version")
    private Long version;
    @Column(name = "reserved_by", length = 50)
    private String reservedBy;
    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;
    @Column(name = "booking_id")
    private Long bookingId;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    // Auto set created time
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getRowNo() {
        return rowNo;
    }

    public void setRowNo(String rowNo) {
        this.rowNo = rowNo;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public void setSeatType(SeatType seatType) {
        this.seatType = seatType;
    }

    @Override
    public String toString() {
        return "Seat{" +
                "seatId=" + seatId +
                ", eventId=" + eventId +
                ", seatNumber='" + seatNumber + '\'' +
                ", section='" + section + '\'' +
                ", rowNo='" + rowNo + '\'' +
                ", seatType=" + seatType +
                ", price=" + price +
                ", status=" + status +
                ", version=" + version +
                ", reservedBy='" + reservedBy + '\'' +
                ", reservedUntil=" + reservedUntil +
                ", bookingId=" + bookingId +
                ", createdAt=" + createdAt +
                '}';
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getReservedBy() {
        return reservedBy;
    }

    public void setReservedBy(String reservedBy) {
        this.reservedBy = reservedBy;
    }

    public LocalDateTime getReservedUntil() {
        return reservedUntil;
    }

    public void setReservedUntil(LocalDateTime reservedUntil) {
        this.reservedUntil = reservedUntil;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
// getters & setters
}