package com.deepak.ticketflow.repository;

import com.deepak.ticketflow.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
	Optional<Seat> findByEventIdAndSeatNumber(Long eventId, String seatNumber);
    List<Seat> findSeatsByEventId(Long eventId);
}
