package com.deepak.ticketflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deepak.ticketflow.model.BookingSeat;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    public List<BookingSeat> findByBookingId(Long bookingId);
}