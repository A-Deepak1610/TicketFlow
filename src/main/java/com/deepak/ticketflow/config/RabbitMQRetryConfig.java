// config/RabbitMQRetryConfig.java
package com.ticketflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitMQRetryConfig {

    @Value("${spring.rabbitmq.email.queue}.dlq")
    private String deadLetterQueue;

    @Value("${spring.rabbitmq.email.exchange}.dlx")
    private String deadLetterExchange;

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    @Bean
    public TopicExchange emailDeadLetterExchange() {
        return ExchangeBuilder.topicExchange(deadLetterExchange).durable(true).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(emailDeadLetterQueue())
                .to(emailDeadLetterExchange())
                .with("email.notification.dead");
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        
        // Retry configuration
        factory.setAdviceChain(RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(1000, 2, 10000)
                .build());
        
        return factory;
    }
}