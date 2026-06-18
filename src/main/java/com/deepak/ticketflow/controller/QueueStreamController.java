package com.deepak.ticketflow.controller;

import com.deepak.ticketflow.filters.CustomUserPrincipal;
import com.deepak.ticketflow.service.queue.SseNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Virtual Queue Live Stream", description = "Server-Sent Events (SSE) endpoints for real-time queue notifications and testing")
public class QueueStreamController {

    private final SseNotificationService notificationService;

    @Operation(
        summary = "Stream Queue Updates",
        description = "Subscribes to real-time status and position updates for the virtual queue using Server-Sent Events (SSE)."
    )
    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestParam(required = false) Long eventId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        System.out.println("=========================CONNECTED==========================");

        return notificationService.subscribe(  
                principal.getUserId(), eventId);
    }
    @Operation(
        summary = "Send Test Event",
        description = "Sends a mock test message through the SSE stream to verify the connection is active."
    )
    @GetMapping("/test")
    public String test(@AuthenticationPrincipal CustomUserPrincipal principal) throws IOException {
        notificationService.sendMessage("Hello from server",principal.getUserId());
        return "sent";
    }
}