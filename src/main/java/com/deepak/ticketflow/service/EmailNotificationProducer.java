// service/EmailNotificationProducer.java
package com.deepak.ticketflow.service;

import com.ticketflow.dto.EmailNotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.email.exchange}")
    private String emailExchange;

    @Value("${spring.rabbitmq.email.routing-key}")
    private String emailRoutingKey;

    public void sendBookingConfirmationEmail(EmailNotificationDTO emailDTO) {
        try {
            log.info("Sending email notification to queue for booking: {}", 
                    emailDTO.getBookingReference());
            
            rabbitTemplate.convertAndSend(
                emailExchange, 
                emailRoutingKey, 
                emailDTO,
                message -> {
                    message.getMessageProperties().setHeader("message-type", "booking-confirmation");
                    message.getMessageProperties().setExpiration("3600000"); // 1 hour TTL
                    return message;
                }
            );
            
            log.info("Email notification published successfully for booking: {}", 
                    emailDTO.getBookingReference());
        } catch (Exception e) {
            log.error("Failed to publish email notification for booking: {}", 
                    emailDTO.getBookingReference(), e);
            throw new RuntimeException("Failed to queue email notification", e);
        }
    }
}