# Distributed Notification System

A production-grade, event-driven notification platform inspired by real-time
delivery systems like WhatsApp and Uber. Built to demonstrate distributed systems
design, backend engineering, and scalable microservice patterns.

---

## Architecture Overview

```
Client → Spring Boot API → Kafka → Notification Consumer → PostgreSQL
                        ↘ Redis                         ↘ WebSocket → Client
```

| Layer                | Technology         | Purpose                                    |
| -------------------- | ------------------ | ------------------------------------------ |
| API Server           | Spring Boot 3.x    | REST endpoints + WebSocket broker          |
| Message Queue        | Apache Kafka       | Async event streaming                      |
| Cache / Rate Limiter | Redis              | Per-user rate limiting with sliding window |
| Database             | PostgreSQL         | Persistent notification storage            |
| Real-time Push       | WebSockets (STOMP) | Live delivery to connected clients         |
| Containerization     | Docker Compose     | Local infrastructure orchestration         |

---

## Key Design Decisions

**Why Kafka instead of direct DB writes?**
Decouples the API from processing. The producer returns immediately after
publishing — consumers handle persistence and push asynchronously. This means
the system stays responsive under load and can scale consumers independently.

**Why manual Kafka acknowledgment?**
Ensures at-least-once delivery. The consumer only commits the offset after
successfully saving to PostgreSQL and pushing via WebSocket. If either fails,
the message is retried automatically.

**Why Redis for rate limiting?**
Redis INCR is atomic — safe for concurrent requests from the same user hitting
multiple instances. The key expires automatically after the window, making it
a self-cleaning sliding window implementation.

**Idempotency via eventId**
Every notification carries a UUID eventId. The consumer checks for duplicates
before processing, so retried Kafka messages never create duplicate notifications
in the database.

---

## Project Structure

```
src/main/java/com/aman/notification_system/
├── config/
│   ├── KafkaConfig.java          # Topic creation, DLT, retry error handler
│   ├── RedisConfig.java          # RedisTemplate with JSON serialization
│   └── WebSocketConfig.java      # STOMP endpoint with SockJS fallback
├── constants/
│   └── KafkaConstants.java       # Topic and consumer group names
├── controller/
│   └── NotificationController.java
├── dto/
│   ├── NotificationEvent.java    # Kafka message payload
│   └── NotificationRequest.java  # Validated REST request body
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── NotificationNotFoundException.java
│   └── RateLimitException.java
├── model/
│   └── Notification.java         # JPA entity with audit timestamps
├── repository/
│   └── NotificationRepository.java
└── service/
    ├── NotificationConsumer.java  # Kafka listener
    ├── NotificationProducer.java  # Kafka publisher
    ├── NotificationService.java   # Orchestration layer
    └── RateLimiterService.java    # Redis sliding window
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

Starts Kafka, Zookeeper, PostgreSQL, and Redis locally.

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

### Get unread count

```http
GET /api/v1/notifications/{userId}/unread-count
```

Response:

```json
{
  "unreadCount": 5
}
```

### Mark all as read

```http
PATCH /api/v1/notifications/{userId}/mark-all-read
```

### Mark one as read

```http
PATCH /api/v1/notifications/{notificationId}/read
```

---

## WebSocket Usage

Connect to `ws://localhost:8080/ws` using a STOMP client and subscribe to:

```
/topic/user/{userId}
```

Every notification sent for that user is pushed in real time to all
connected subscribers on that topic.

---

## Rate Limiting

Each user is limited to **10 requests per 60 seconds** (configurable in
`application-local.yml`). Exceeding the limit returns:

```http
HTTP 429 Too Many Requests
{
  "status": 429,
  "message": "Rate limit exceeded for user: user1"
}
```

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.5**
- **Apache Kafka** — async event streaming
- **Redis** — rate limiting
- **PostgreSQL** — persistent storage
- **WebSockets / STOMP** — real-time push
- **Docker Compose** — local infrastructure
- **Lombok** — boilerplate reduction
- **Maven** — build tool
