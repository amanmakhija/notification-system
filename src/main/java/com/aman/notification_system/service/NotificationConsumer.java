package com.aman.notification_system.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aman.notification_system.constants.KafkaConstants;
import com.aman.notification_system.dto.NotificationEvent;
import com.aman.notification_system.model.Notification;
import com.aman.notification_system.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    @KafkaListener(
        topics = KafkaConstants.NOTIFICATION_TOPIC,
        groupId = KafkaConstants.NOTIFICATION_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, NotificationEvent> record, Acknowledgment ack) {
        NotificationEvent event = record.value();
        log.info("Consumed event: eventId={}, userId={}, partition={}, offset={}",
                event.getEventId(), event.getUserId(),
                record.partition(), record.offset());

        try {
            if (notificationRepository.existsByEventId(event.getEventId())) {
                log.warn("Duplicate event detected, skipping: {}", event.getEventId());
                ack.acknowledge();
                return;
            }

            Notification notification = Notification.builder()
                    .eventId(event.getEventId())
                    .userId(event.getUserId())
                    .message(event.getMessage())
                    .type(event.getType())
                    .build();
            notificationRepository.save(notification);

            messagingTemplate.convertAndSend(
                    "/topic/user/" + event.getUserId(), event
            );

            ack.acknowledge();
            log.info("Successfully processed notification for user: {}", event.getUserId());

        } catch (Exception ex) {
            log.error("Error processing notification event: {}", event.getEventId(), ex);
            throw ex;
        }
    }
}