package com.deepak.ticketflow.config;

import com.deepak.ticketflow.Enum.SeatType;
import java.math.BigDecimal;

public class SeatConfig {

    public record SectionConfig(
            SeatType seatType,
            double percentage,
            int seatsPerRow,
            BigDecimal price,
            String sectionName
    ) {}

    public static final SectionConfig[] SECTIONS = {
            new SectionConfig(SeatType.VIP,     0.05, 5,  new BigDecimal("5000.00"), "VIP"),
            new SectionConfig(SeatType.PREMIUM, 0.15, 10, new BigDecimal("2000.00"), "PREMIUM"),
            new SectionConfig(SeatType.REGULAR, 0.80, 20, new BigDecimal("500.00"),  "REGULAR")
    };
}