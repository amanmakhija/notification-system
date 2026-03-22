package com.aman.notification_system.service;

import com.aman.notification_system.constants.KafkaConstants;
import com.aman.notification_system.dto.NotificationEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "kafkaProducer")
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

    public void sendNotificationFallback(NotificationEvent event, Throwable t) {
        log.error("Circuit breaker triggered for user: {}. Reason: {}",
                event.getUserId(), t.getMessage());
        // In production this would push to a fallback queue or persist for retry
    }
}