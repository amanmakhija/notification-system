package com.aman.notification_system.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.aman.notification_system.constants.KafkaConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${notification.kafka.partitions}")
    private int partitions;

    @Value("${notification.kafka.replication-factor}")
    private int replicationFactor;

    @Bean
    public NewTopic notificationTopic() {
        return TopicBuilder
                .name(KafkaConstants.NOTIFICATION_TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder
                .name(KafkaConstants.NOTIFICATION_TOPIC + KafkaConstants.DLT_SUFFIX)
                .partitions(1)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff backOff = new FixedBackOff(2000L, 3L);
        DefaultErrorHandler handler = new DefaultErrorHandler(backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}