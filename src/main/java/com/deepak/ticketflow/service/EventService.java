package com.deepak.ticketflow.service;

import com.deepak.ticketflow.Enum.EventStatus;
import com.deepak.ticketflow.dto.CreateEventRequest;
import com.deepak.ticketflow.dto.EventWithSeatsResponse;
import com.deepak.ticketflow.dto.SeatResponse;
import com.deepak.ticketflow.model.Event;
import com.deepak.ticketflow.model.Seat;
import com.deepak.ticketflow.repository.EventRepository;
import com.deepak.ticketflow.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {
    @Autowired
    EventRepository eventRepository;
    @Autowired
    SeatGenerationService seatGenerationService;
    @Autowired
    SeatRepository seatRepository;
    public EventWithSeatsResponse createEvent(CreateEventRequest request) {
        Event event = new Event();
        event.setEventName(request.getEventName());
        event.setEventDate(request.getEventDate());
        event.setVenueName(request.getVenueName());
        event.setTotalSeats(request.getTotalSeats());
        event.setAvailableSeats(request.getTotalSeats());
        event.setSaleStartTime(request.getSaleStartTime());
        event.setStatus(EventStatus.UPCOMING);

        Event savedEvent = eventRepository.save(event);
        List<Seat> seats = seatGenerationService.generateSeats(savedEvent.getEventId(), savedEvent.getTotalSeats());
        EventWithSeatsResponse response = new EventWithSeatsResponse();
        response.setEventId(savedEvent.getEventId());
        response.setEventName(savedEvent.getEventName());
        response.setEventDate(savedEvent.getEventDate());
        response.setVenueName(savedEvent.getVenueName());
        response.setTotalSeats(savedEvent.getTotalSeats());
        response.setAvailableSeats(savedEvent.getAvailableSeats());
        response.setStatus(savedEvent.getStatus().name());
        List<SeatResponse> seatResponses = seats.stream().map(seat -> {
            SeatResponse s = new SeatResponse();
            s.setSeatId(seat.getSeatId());
            s.setSeatNumber(seat.getSeatNumber());
            s.setSection(seat.getSection());
            s.setRowNo(seat.getRowNo());
            s.setSeatType(seat.getSeatType().name());
            s.setPrice(seat.getPrice().doubleValue());
            s.setStatus(seat.getStatus().name());
            return s;
        }).toList();
        response.setSeats(seatResponses);
        return response;
    }
    public List<Event> getEvents(){
        return eventRepository.findAll();
    }
    public EventWithSeatsResponse getEventDatails(Long eventId){
        EventWithSeatsResponse response = new EventWithSeatsResponse();
        Event savedEvent=eventRepository.findById(eventId).get();
        List<Seat> seats=seatRepository.findSeatsByEventId(eventId);
        response.setEventId(savedEvent.getEventId());
        response.setEventName(savedEvent.getEventName());
        response.setEventDate(savedEvent.getEventDate());
        response.setVenueName(savedEvent.getVenueName());
        response.setTotalSeats(savedEvent.getTotalSeats());
        response.setAvailableSeats(savedEvent.getAvailableSeats());
        response.setStatus(savedEvent.getStatus().name());
        List<SeatResponse> seatResponses = seats.stream().map(seat -> {
            SeatResponse s = new SeatResponse();
            s.setSeatId(seat.getSeatId());
            s.setSeatNumber(seat.getSeatNumber());
            s.setSection(seat.getSection());
            s.setRowNo(seat.getRowNo());
            s.setSeatType(seat.getSeatType().name());
            s.setPrice(seat.getPrice().doubleValue());
            s.setStatus(seat.getStatus().name());
            return s;
        }).toList();
        response.setSeats(seatResponses);
        return response;
    }
}
