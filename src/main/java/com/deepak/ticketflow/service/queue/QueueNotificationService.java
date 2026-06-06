package com.deepak.ticketflow.service.queue;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueueNotificationService {

    private final Map<Integer, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);//forever connection
        emitters.put(userId, emitter);

        emitter.onCompletion(() ->//close connection
                emitters.remove(userId));
        emitter.onTimeout(() ->
                emitters.remove(userId));
        return emitter;
    }
    public void sendPosition(Integer userId, int position) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null)
            return;
        try {
            emitter.send(
                    Map.of(
                            "type", "POSITION",
                            "position", position
                    ));
        } catch (IOException e) {
            emitters.remove(userId);
        }
    }
    public void sendBookingWindow(Integer userId, String token, int expiryMinutes) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null)
            return;
        try {
            emitter.send(
                    Map.of(
                            "type", "BOOKING_ALLOWED",
                            "token", token,
                            "expiresIn", expiryMinutes * 60
                    ));

        } catch (IOException e) {
            emitters.remove(userId);
        }
    }
    public void sendMessage(String msg,Integer userId) throws IOException {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            emitter.send(msg);
        }
    }

}