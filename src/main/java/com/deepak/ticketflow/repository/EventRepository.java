package com.deepak.ticketflow.repository;

import com.deepak.ticketflow.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event,Long> {
}
