package com.aman.notification_system.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.aman.notification_system.constants.KafkaConstants;
import com.aman.notification_system.dto.NotificationEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationDLTConsumer {

    @KafkaListener(
        topics = KafkaConstants.NOTIFICATION_TOPIC + KafkaConstants.DLT_SUFFIX,
        groupId = KafkaConstants.NOTIFICATION_GROUP + "-dlt"
    )
    public void consumeFromDLT(ConsumerRecord<String, NotificationEvent> record) {
        NotificationEvent event = record.value();
        log.error("DLT: Failed notification event received — eventId={}, userId={}, partition={}, offset={}",
                event != null ? event.getEventId() : "null",
                event != null ? event.getUserId() : "null",
                record.partition(),
                record.offset());
        // In production: persist to a failed_notifications table
        // alert on-call, or trigger manual review workflow
    }
}