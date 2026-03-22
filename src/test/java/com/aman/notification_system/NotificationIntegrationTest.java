package com.aman.notification_system;

import com.aman.notification_system.dto.NotificationRequest;
import com.aman.notification_system.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSendNotificationAndPersistToDatabase() throws Exception {
        NotificationRequest request = new NotificationRequest();
        request.setUserId("test-user-1");
        request.setMessage("Integration test notification");
        request.setType("TEST");

        mockMvc.perform(post("/api/v1/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        // Wait for Kafka consumer to process asynchronously
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = notificationRepository.countByUserIdAndReadFalse("test-user-1");
            assertThat(count).isGreaterThan(0);
        });
    }

    @Test
    void shouldReturnUnreadCount() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/test-user-1/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber());
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        NotificationRequest request = new NotificationRequest();
        // missing required fields

        mockMvc.perform(post("/api/v1/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }
}