package com.deepak.ticketflow.jobs;

import com.deepak.ticketflow.Enum.EventStatus;
import com.deepak.ticketflow.model.Event;
import com.deepak.ticketflow.repository.EventRepository;
import com.deepak.ticketflow.service.queue.SseNotificationService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenBookingJob implements Job {

    private final EventRepository eventRepository;
    private final SseNotificationService sseNotificationService;

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {

        Long eventId =
                context.getMergedJobDataMap()
                       .getLong("eventId");

        Event event = eventRepository.findById(eventId)
                .orElseThrow();

        event.setStatus(EventStatus.OPEN);

        eventRepository.save(event);

        sseNotificationService.sendEventStatusUpdate(eventId, "OPEN");

        System.out.println("Booking opened");
    }
}