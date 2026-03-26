package com.deepak.ticketflow.repository;

import com.deepak.ticketflow.model.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
}