# Distributed Notification System

A production-grade, event-driven notification platform inspired by real-time
delivery systems like WhatsApp and Uber. Built to demonstrate distributed systems
design, backend engineering, and scalable architecture patterns.

---

## Architecture Overview

```
Client → Spring Boot API → Kafka → Notification Consumer → PostgreSQL
              ↓                           ↓
            Redis                    WebSocket → Client
              ↓
        Rate Limiter
              ↓
        Circuit Breaker
              ↓
          Kafka DLT
```

| Layer                | Technology             | Purpose                               |
| -------------------- | ---------------------- | ------------------------------------- |
| API Server           | Spring Boot 3.5        | REST endpoints + WebSocket broker     |
| Message Queue        | Apache Kafka           | Async event streaming with DLT        |
| Cache / Rate Limiter | Redis                  | Per-user sliding window rate limiting |
| Database             | PostgreSQL             | Persistent notification storage       |
| Real-time Push       | WebSockets (STOMP)     | Live delivery to connected clients    |
| Fault Tolerance      | Custom Circuit Breaker | Kafka producer failure isolation      |
| Observability        | Actuator + Prometheus  | Health checks and metrics             |
| Containerization     | Docker Compose         | Local infrastructure orchestration    |

---

## Key Design Decisions

**Why Kafka instead of direct DB writes?**
Decouples the API from processing. The producer returns immediately after
publishing — consumers handle persistence and push asynchronously. This means
the system stays responsive under load and consumers can scale independently
without affecting the API layer.

**Why manual Kafka acknowledgment?**
Ensures at-least-once delivery. The consumer only commits the offset after
successfully saving to PostgreSQL and pushing via WebSocket. If either step
fails, the message is retried automatically without data loss.

**Why Redis for rate limiting?**
Redis INCR is atomic — safe for concurrent requests from the same user hitting
multiple instances simultaneously. The key expires automatically after the
window closes, making it a self-cleaning sliding window with no background jobs.

**Idempotency via eventId**
Every notification carries a UUID eventId generated at the API layer. The
consumer checks for duplicates before processing, so Kafka retries never
create duplicate notifications in the database.

**Correlation ID for end-to-end tracing**
Every HTTP request generates or inherits an X-Correlation-ID header. This ID
is embedded in the Kafka event payload and restored in MDC when the consumer
processes it — meaning every log line from API to database carries the same
trace ID, making debugging across async hops straightforward.

**Dead Letter Topic for failed events**
Events that exhaust all retry attempts are routed to `notification.events.DLT`.
A dedicated consumer logs these for manual review, ensuring no event is
silently dropped and failed notifications are always recoverable.

**Circuit breaker on Kafka producer**
A failure counter tracks consecutive Kafka send failures. Once the threshold
is breached the circuit opens and new events are routed to a fallback handler
instead of hammering a degraded broker, preventing cascade failures.

**Delivery status lifecycle**
Each notification moves through a defined lifecycle:
`PENDING → SENT → DELIVERED → READ → FAILED`
This enables read receipts, unread badges, and delivery analytics without
additional infrastructure.

---

## Project Structure

```
src/main/java/com/aman/notification_system/
├── config/
│   ├── KafkaConfig.java           # Topic creation, DLT, retry error handler
│   ├── RedisConfig.java           # RedisTemplate with JSON serialization
│   └── WebSocketConfig.java       # STOMP endpoint with SockJS fallback
├── constants/
│   └── KafkaConstants.java        # Topic and consumer group names
├── controller/
│   └── NotificationController.java
├── dto/
│   ├── NotificationEvent.java     # Kafka message payload with correlationId
│   └── NotificationRequest.java   # Validated REST request body
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── NotificationNotFoundException.java
│   └── RateLimitException.java
├── filter/
│   └── CorrelationIdFilter.java   # X-Correlation-ID propagation via MDC
├── model/
│   └── Notification.java          # JPA entity with DeliveryStatus enum
├── repository/
│   └── NotificationRepository.java
└── service/
    ├── NotificationConsumer.java   # Kafka listener with idempotency check
    ├── NotificationDLTConsumer.java # Dead letter topic handler
    ├── NotificationProducer.java   # Kafka publisher with circuit breaker
    ├── NotificationService.java    # Orchestration layer
    └── RateLimiterService.java     # Redis sliding window rate limiter
```

---

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop

### Start infrastructure

```bash
docker compose up -d
```

Starts the following services locally:

| Service      | Port |
| ------------ | ---- |
| Apache Kafka | 9092 |
| Zookeeper    | 2181 |
| PostgreSQL   | 5432 |
| Redis        | 6379 |

### Run the application

```bash
mvn spring-boot:run
```

App starts on `http://localhost:8080`

---

## API Reference

### Send a notification

```http
POST /api/v1/notifications/send
Content-Type: application/json

{
  "userId": "user1",
  "message": "You have a new message",
  "type": "CHAT"
}
```

Response `202 Accepted`:

```json
{
  "message": "Notification queued for user: user1"
}
```

### Get notifications (paginated)

```http
GET /api/v1/notifications/{userId}?page=0&size=20
```

Response `200 OK`:

```json
{
  "content": [
    {
      "id": 1,
      "userId": "user1",
      "message": "You have a new message",
      "type": "CHAT",
      "read": false,
      "deliveryStatus": "SENT",
      "createdAt": "2026-03-23T00:50:38"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### Get unread count

```http
GET /api/v1/notifications/{userId}/unread-count
```

Response `200 OK`:

```json
{
  "unreadCount": 5
}
```

### Mark all as read

```http
PATCH /api/v1/notifications/{userId}/mark-all-read
```

Response `200 OK`:

```json
{
  "updated": 5
}
```

### Mark one as read

```http
PATCH /api/v1/notifications/{notificationId}/read
```

Response `204 No Content`

---

## Observability

### Health check

```http
GET /actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

### Prometheus metrics

```http
GET /actuator/prometheus
```

### All available endpoints

```http
GET /actuator
```

---

## Rate Limiting

Each user is limited to **10 requests per 60 seconds** (configurable in
`application-local.yml`). Exceeding the limit returns:

```http
HTTP 429 Too Many Requests
```

```json
{
  "status": 429,
  "message": "Rate limit exceeded for user: user1"
}
```

Rate limiting applies to write operations only. Read operations are
not rate limited to avoid degrading the user experience.

---

## Error Handling

| Scenario                    | HTTP Status                |
| --------------------------- | -------------------------- |
| Invalid request body        | 400 Bad Request            |
| Notification not found      | 404 Not Found              |
| Rate limit exceeded         | 429 Too Many Requests      |
| Unexpected server error     | 500 Internal Server Error  |
| Kafka producer circuit open | Fallback handler triggered |
| Consumer exhausts retries   | Routed to DLT              |

---

## Notification Delivery Lifecycle

```
PENDING → SENT → DELIVERED → READ
                           ↘ FAILED (on consumer error)
```

| Status  | When set                                                |
| ------- | ------------------------------------------------------- |
| PENDING | Event created at API layer                              |
| SENT    | Consumer successfully persists and pushes via WebSocket |
| READ    | User marks notification as read                         |
| FAILED  | Consumer encounters unrecoverable error                 |

---

## WebSocket Usage

Connect to `ws://localhost:8080/ws` using a STOMP client and subscribe to:

```
/topic/user/{userId}
```

Every notification sent for that user is pushed in real time to all
connected subscribers on that topic. The pushed payload includes the
full `NotificationEvent` with eventId, correlationId, message, type,
and timestamp.

---

## Running Tests

```bash
mvn test
```

Integration tests use **Testcontainers** to spin up real PostgreSQL and
Kafka instances per test run — no mocking, no in-memory fakes. Tests
verify the full async pipeline from HTTP request through Kafka to
database persistence using Awaitility for async assertions.

---

## Tech Stack

| Technology         | Version | Purpose               |
| ------------------ | ------- | --------------------- |
| Java               | 21      | Language              |
| Spring Boot        | 3.5     | Application framework |
| Apache Kafka       | 7.6.0   | Async event streaming |
| Redis              | 7.2     | Rate limiting         |
| PostgreSQL         | 16      | Persistent storage    |
| WebSockets / STOMP | -       | Real-time push        |
| Docker Compose     | -       | Local infrastructure  |
| Testcontainers     | -       | Integration testing   |
| Lombok             | -       | Boilerplate reduction |
| Maven              | 3.9     | Build tool            |
