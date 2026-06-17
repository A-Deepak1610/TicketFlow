package com.deepak.ticketflow.service;

import com.deepak.ticketflow.jobs.CloseBookingJob;
import com.deepak.ticketflow.jobs.OpenBookingJob;
import com.deepak.ticketflow.model.Event;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
public class EventSchedulerService {

    private final Scheduler scheduler;

    public void scheduleBookingOpen(Event event) {

        try {

            JobDetail jobDetail =
                    JobBuilder.newJob(OpenBookingJob.class)
                            .withIdentity(
                                    "open-" + event.getEventId())
                            .usingJobData(
                                    "eventId",
                                    event.getEventId())
                            .storeDurably()
                            .build();

            Trigger trigger =
                    TriggerBuilder.newTrigger()
                            .withIdentity(
                                    "open-trigger-" + event.getEventId())
                            .startAt(
                                    Timestamp.valueOf(
                                            event.getSaleStartTime()))
                            .build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    public void scheduleBookingClose(Event event) {

        try {

            JobDetail jobDetail =
                    JobBuilder.newJob(CloseBookingJob.class)
                            .withIdentity(
                                    "close-" + event.getEventId())
                            .usingJobData(
                                    "eventId",
                                    event.getEventId())
                            .storeDurably()
                            .build();

            Trigger trigger =
                    TriggerBuilder.newTrigger()
                            .withIdentity(
                                    "close-trigger-" + event.getEventId())
                            .startAt(
                                    Timestamp.valueOf(
                                            event.getEventDate()))
                            .build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }
}