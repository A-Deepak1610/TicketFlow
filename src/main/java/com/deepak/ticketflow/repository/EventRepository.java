package com.deepak.ticketflow.repository;

import com.deepak.ticketflow.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event,Long> {
    @Modifying
    @Query("UPDATE Event e SET e.availableSeats = :newCount, e.version = e.version + 1 " +
            "WHERE e.eventId = :eventId AND e.version = :version")
    int updateAvailableSeats(@Param("eventId") Long eventId,
                             @Param("version") Long version,
                             @Param("newCount") int newCount);
}
