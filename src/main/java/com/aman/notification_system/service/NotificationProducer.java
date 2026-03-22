package com.aman.notification_system.service;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.aman.notification_system.constants.KafkaConstants;
import com.aman.notification_system.dto.NotificationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void sendNotification(NotificationEvent event) {
        CompletableFuture<SendResult<String, NotificationEvent>> future =
                kafkaTemplate.send(KafkaConstants.NOTIFICATION_TOPIC, event.getUserId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send notification event for user: {} - {}",
                        event.getUserId(), ex.getMessage());
            } else {
                log.debug("Notification sent to partition: {}, offset: {}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}