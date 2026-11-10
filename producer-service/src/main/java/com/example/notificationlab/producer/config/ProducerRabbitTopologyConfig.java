package com.example.notificationlab.producer.config;

import com.example.notificationlab.contracts.RabbitNames;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProducerRabbitTopologyConfig {

    @Bean
    DirectExchange notificationDirectExchange() {
        return new DirectExchange(RabbitNames.DIRECT_EXCHANGE, true, false);
    }
}
