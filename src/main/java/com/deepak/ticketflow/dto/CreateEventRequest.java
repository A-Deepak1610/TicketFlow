package com.deepak.ticketflow.dto;

import java.time.LocalDateTime;

public class CreateEventRequest {
    private String eventName;
    private LocalDateTime eventDate;
    private String venueName;

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public LocalDateTime getSaleStartTime() {
        return saleStartTime;
    }

    public void setSaleStartTime(LocalDateTime saleStartTime) {
        this.saleStartTime = saleStartTime;
    }

    private int totalSeats;

    @Override
    public String toString() {
        return "CreateEventRequest{" +
                "eventName='" + eventName + '\'' +
                ", eventDate=" + eventDate +
                ", venueName='" + venueName + '\'' +
                ", totalSeats=" + totalSeats +
                ", saleStartTime=" + saleStartTime +
                '}';
    }

    private LocalDateTime saleStartTime;

}