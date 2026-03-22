package com.aman.notification_system.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aman.notification_system.dto.NotificationEvent;
import com.aman.notification_system.dto.NotificationRequest;
import com.aman.notification_system.exception.NotificationNotFoundException;
import com.aman.notification_system.model.Notification;
import com.aman.notification_system.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationProducer notificationProducer;
    private final RateLimiterService rateLimiterService;

    public void sendNotification(NotificationRequest request) {
        rateLimiterService.checkRateLimit(request.getUserId());

        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .message(request.getMessage())
                .type(request.getType())
                .timestamp(LocalDateTime.now())
                .build();

        notificationProducer.sendNotification(event);
        log.info("Notification event published for user: {}", request.getUserId());
    }

    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(String userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public int markAllAsRead(String userId) {
        int updated = notificationRepository.markAllAsReadByUserId(userId);
        log.info("Marked {} notifications as read for user: {}", updated, userId);
        return updated;
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.setRead(true);
        notificationRepository.save(notification);
    }
}