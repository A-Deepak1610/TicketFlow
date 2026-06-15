package com.deepak.ticketflow.service;

import com.deepak.ticketflow.dto.EmailNotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @RabbitListener(queues = "${spring.rabbitmq.email.queue}", concurrency = "3-10")
    public void consumeBookingConfirmation(EmailNotificationDTO emailDTO) {
        log.info("Consumed email notification from queue for booking: {}", 
                emailDTO.getBookingReference());
        
        try {
            sendHtmlEmail(emailDTO);
            log.info("Email sent successfully for booking: {}", emailDTO.getBookingReference());
        } catch (Exception e) {
            log.error("Failed to send email for booking: {}", emailDTO.getBookingReference(), e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

   // Update the sendHtmlEmail method in EmailNotificationConsumer
    private void sendHtmlEmail(EmailNotificationDTO emailDTO) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setTo(emailDTO.getToEmail());
        helper.setSubject("Booking Confirmation - " + emailDTO.getBookingReference());
        
        // ➕ ADD THIS - Set From address
        helper.setFrom("noreply@ticketflow.com");  // Or read from properties
        
        Context context = new Context();
        context.setVariable("booking", emailDTO);
        
        String htmlContent = templateEngine.process("booking-confirmation-email", context);
        helper.setText(htmlContent, true);
        
        mailSender.send(message);
    }
}