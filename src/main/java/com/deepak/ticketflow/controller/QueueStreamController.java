package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.filters.CustomUserPrincipal;
import com.deepak.ticketflow.service.queue.SseNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InvalidObjectException;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueStreamController {

    private final SseNotificationService notificationService;

    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestParam(required = false) Long eventId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        System.out.println("=========================CONNECTED==========================");

        return notificationService.subscribe(
                principal.getUserId(), eventId);
    }
    @GetMapping("/test")
    public String test(@AuthenticationPrincipal CustomUserPrincipal principal) throws IOException {
        notificationService.sendMessage("Hello from server",principal.getUserId());
        return "sent";
    }
}