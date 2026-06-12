
package com.ticketflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationDTO {
    private String toEmail;
    private String userName;
    private String bookingReference;
    private Long eventId;
    private String eventName;
    private LocalDateTime eventDateTime;
    private String venueName;
    private List<SeatDetail> seats;
    private BigDecimal totalAmount;
    private LocalDateTime bookingConfirmedAt;
    private String qrCodeUrl; // optional
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatDetail {
        private String seatNumber;
        private String section;
        private BigDecimal price;
    }
}