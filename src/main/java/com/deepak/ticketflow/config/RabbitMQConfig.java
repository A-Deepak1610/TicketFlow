package com.ticketflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.email.exchange}")
    private String emailExchange;

    @Value("${spring.rabbitmq.email.queue}")
    private String emailQueue;

    @Value("${spring.rabbitmq.email.routing-key}")
    private String emailRoutingKey;

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(emailQueue)//A queue stores messages until the consumer processes them.
                .withArgument("x-dead-letter-exchange", emailExchange + ".dlx")//will receive failed messages.
                .withArgument("x-dead-letter-routing-key", emailRoutingKey + ".dead")//The specific routing key used when sending a failed message to the DLX.
                .withArgument("x-max-retries", 3)
                .build();
    }

    @Bean
    public TopicExchange emailExchange() {
        return ExchangeBuilder.topicExchange(emailExchange)
                .durable(true)
                .build();
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder
                .bind(emailQueue())
                .to(emailExchange())
                .with(emailRoutingKey);
    }

    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}