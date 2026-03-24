package com.deepak.ticketflow.service;
import com.deepak.ticketflow.Enum.SeatStatus;
import com.deepak.ticketflow.config.SeatConfig;
import com.deepak.ticketflow.config.SeatConfig.SectionConfig;
import com.deepak.ticketflow.model.Seat;
import com.deepak.ticketflow.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeatGenerationService {

    @Autowired
    private SeatRepository seatRepository;

    /**
     * Generates seats for an event based on totalSeats.
     *
     * Distribution logic:
     *  - VIP     → 5%  of total (min 1 row, seatsPerRow = 5)
     *  - PREMIUM → 15% of total (min 1 row, seatsPerRow = 10)
     *  - REGULAR → 80% of total (remaining seats, seatsPerRow = 20)
     *
     * Row naming: R1, R2, R3...  (per section, resets each section)
     * Seat numbering: VIP-R1-1, PREMIUM-R2-5, REGULAR-R3-20
     */
    @Transactional
    public List<Seat> generateSeats(Long eventId, int totalSeats) {
        List<Seat> allSeats = new ArrayList<>();
        int[] seatCounts = distributeSeatCounts(totalSeats);
        for (int i = 0; i < SeatConfig.SECTIONS.length; i++) {
            SectionConfig config = SeatConfig.SECTIONS[i];
            int count = seatCounts[i];
            List<Seat> sectionSeats = generateSectionSeats(eventId, config, count);
            allSeats.addAll(sectionSeats);
        }
        return seatRepository.saveAll(allSeats);
    }

    /**
     * Distributes total seats across sections proportionally.
     * Remainder seats go to REGULAR to ensure totalSeats is exact.
     */
    private int[] distributeSeatCounts(int totalSeats) {
        int[] counts = new int[SeatConfig.SECTIONS.length];
        int assigned = 0;
        for (int i = 0; i < SeatConfig.SECTIONS.length - 1; i++) {
            counts[i] = (int) Math.round(totalSeats * SeatConfig.SECTIONS[i].percentage());
            assigned += counts[i];
        }
        counts[SeatConfig.SECTIONS.length - 1] = totalSeats - assigned;
        return counts;
    }

    /**
     * Generates seats row-by-row for a section.
     *
     * Row format  : R1, R2, R3 ...
     * Seat format : SECTION-R1-SEATNUMBER  e.g. VIP-R1-1
     */
    private List<Seat> generateSectionSeats(Long eventId, SectionConfig config, int totalForSection) {
        List<Seat> seats = new ArrayList<>();

        int seatsPerRow   = config.seatsPerRow();
        int totalRows     = (int) Math.ceil((double) totalForSection / seatsPerRow);
        int seatsAssigned = 0;

        for (int row = 1; row <= totalRows; row++) {
            String rowLabel      = "R" + row;
            int seatsInThisRow   = Math.min(seatsPerRow, totalForSection - seatsAssigned);

            for (int seatNum = 1; seatNum <= seatsInThisRow; seatNum++) {
                Seat seat = new Seat();
                seat.setEventId(eventId);
                seat.setSection(config.sectionName());
                seat.setSeatType(config.seatType());
                seat.setPrice(config.price());
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setRowNo(rowLabel);
                seat.setSeatNumber(config.sectionName() + "-" + rowLabel + "-" + seatNum);
                // e.g. VIP-R1-1, PREMIUM-R2-5, REGULAR-R10-20
                seats.add(seat);
            }

            seatsAssigned += seatsInThisRow;
        }

        return seats;
    }
}