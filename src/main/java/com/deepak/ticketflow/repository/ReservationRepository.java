package com.deepak.ticketflow.repository;

import com.deepak.ticketflow.Enum.ReservationStatus;
import com.deepak.ticketflow.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findBySeatIdAndStatus(Long seatId, ReservationStatus status);

    // Fetch all active reservations that have passed their expiry
    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
    List<Reservation> findExpiredReservations(@Param("now") LocalDateTime now);
}