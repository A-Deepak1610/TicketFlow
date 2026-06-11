package com.deepak.ticketflow.service.queue;

import com.deepak.ticketflow.Enum.UserType;
import com.deepak.ticketflow.dto.queue.QueuePositionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SseNotificationService {
    private final StringRedisTemplate redis;
    private final VirtualQueueService queueService;
    private final Map<Integer, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<Integer, Long> userEvents = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer userId) {
        return subscribe(userId, null);
    }

    public SseEmitter subscribe(Integer userId, Long eventId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // forever connection
        emitters.put(userId, emitter);
        if (eventId != null) {
            userEvents.put(userId, eventId);
        } else {
            userEvents.remove(userId);
        }

        emitter.onCompletion(() -> {
            emitters.remove(userId);
            userEvents.remove(userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            userEvents.remove(userId);
        });
        return emitter;
    }

    public void sendSeatStatusUpdate(Long eventId, String seatNumber, String status) {
        if (eventId == null || seatNumber == null || status == null) {
            return;
        }
        Map<String, Object> update = Map.of(
                "type", "SEAT_STATUS_UPDATE",
                "eventId", eventId,
                "seatNumber", seatNumber,
                "status", status
        );

        for (Map.Entry<Integer, Long> entry : userEvents.entrySet()) {
            if (eventId.equals(entry.getValue())) {
                Integer userId = entry.getKey();
                SseEmitter emitter = emitters.get(userId);
                if (emitter != null) {
                    try {
                        emitter.send(update);
                    } catch (IOException e) {
                        emitters.remove(userId);
                        userEvents.remove(userId);
                    }
                }
            }
        }
    }
    public void sendPosition(Integer userId, QueuePositionResponse response) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null)
            return;
        try {
            emitter.send(response);
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

    public void updateQueuePositions(Long eventId) {
        updateQueuePositions(eventId, UserType.VIP);
        updateQueuePositions(eventId, UserType.NORMAL);
    }

    private void updateQueuePositions(Long eventId, UserType userType) {
        List<String> list = redis.opsForList().range(getQueueKey(eventId, userType), 0, -1);
        if (list == null) {
            return;
        }

        for (String entry : list) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }

            Integer userId = Integer.parseInt(parts[1]);
            QueuePositionResponse response =
                    queueService.getPosition(eventId, userId, userType);

            sendPosition(userId, response);
        }
    }
    private String getQueueKey(Long eventId, UserType userType) {
        return "queue:" + eventId + ":" + userType.name().toLowerCase();
    }
}
