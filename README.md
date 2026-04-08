# TicketFlow

TicketFlow is a Spring Boot backend for high-demand event ticket booking with JWT authentication, virtual queue control, seat reservation, and booking confirmation.

## Booking Flow Diagram
<img width="1536" height="1024" alt="ChatGPT Image Apr 8, 2026, 03_20_14 PM" src="https://github.com/user-attachments/assets/7f77f5f1-dc13-49ee-adba-666d7d3c4626" />

## Features

- User registration, login, refresh token flow
- Role-based access control for admin operations
- Event listing and event details APIs
- Virtual queue with direct/soft/hard queue modes
- Seat reservation and booking confirmation flow
- Idempotent booking confirmation using `Idempotency-Key`
- Redis-backed queue/token handling
- Actuator + Prometheus metrics support

## Feature Coverage Matrix

### In Progress / Planned (Next Up)

- 🔲 WebSocket real-time updates
- 🔲 Payment gateway integration (Stripe/Razorpay sandbox)
- 🔲 Distributed queue/events (Kafka/RabbitMQ)
- 🔲 Load testing proof (JMeter/k6)

### 1) Core Booking Features

- ✔ Event-based ticket booking
- ✔ Seat selection system (seat-level granularity)
- ✔ Real-time seat availability tracking (API-level)
- ✔ Temporary seat reservation (hold system)
- ✔ Booking confirmation flow
- ✔ Booking cancellation/expiry handling

### 2) Concurrency & Race Condition Handling

- ✔ Redis-based distributed locking per seat
- ✔ Prevents double booking
- ✔ Atomic reservation logic
- ✔ Retry mechanism on lock failure
- ✔ Safe concurrent access for multiple users

### 3) Reservation System (Critical)

- ✔ Reservation state persisted in database
- ✔ Temporary booking window (TTL-based)
- ✔ Reservation expiry after timeout
- ✔ Automatic cleanup of expired reservations
- ✔ Recovery-safe approach (DB + Redis hybrid)

### 4) Redis Integration

Using: **Redis**

- ✔ Distributed locking (seat-level)
- ✔ Active user tracking using TTL
- ✔ Booking window management
- ✔ Queue system (FIFO + priority)
- ✔ RPS tracking/rate-control support
- ✔ Temporary fast-access storage

### 5) Scheduling System (Spring Boot)

- ✔ Scheduled cleanup for expired reservations
- ✔ Queue processing scheduler
- ✔ Controlled release of users from queue
- ✔ Background jobs without blocking request flow

### 6) Virtual Queue System (Advanced)

- ✔ Dynamic queue activation based on load
- ✔ FIFO queue for normal users
- ✔ Priority queue for VIP users
- ✔ Queue position tracking
- ✔ Controlled entry into booking system
- ✔ Traffic throttling to protect backend

### 7) Priority Queue / VIP System

- ✔ Separate VIP and normal queue lanes
- ✔ Priority-based access control
- ✔ Configurable serving ratio (for example, VIP/normal)
- ✔ Early-access mechanism for premium users
- ✔ Fairness + premium handling balance

### 8) Load Monitoring & Gatekeeper Logic

- ✔ Active user tracking
- ✔ RPS monitoring
- ✔ Dynamic decision: queue vs direct access
- ✔ Overload protection
- ✔ Smart traffic routing

### 9) Idempotency & Reliability

- ✔ Prevent duplicate bookings
- ✔ Safe retry mechanisms
- ✔ Consistent booking state on failures
- ✔ Fault-tolerant Redis + DB design

### 10) Payment Flow

- ✔ Payment initiation step in booking flow
- ✔ Reservation before payment confirmation
- ✔ Booking confirmation after payment success
- ✔ Payment failure handling (release/recover seats)
- 🔲 External payment gateway sandbox integration (Stripe/Razorpay)

### 11) Messaging System (Optional / Planned)

- ✔ Async-ready architecture and decoupled service boundaries
- 🔲 Broker-backed async workflows via RabbitMQ/Kafka
- 🔲 Broker retry pipelines for payment/DB transient failures

### 12) API Layer (Spring Boot)

- ✔ REST APIs for booking, queue, reservation
- ✔ Entry gatekeeper API behavior
- ✔ Queue status/position API
- ✔ Seat reservation APIs
- ✔ Booking confirmation APIs

### 13) Real-Time Capabilities

- ✔ Live queue and seat state available through polling APIs
- 🔲 WebSocket-based live queue position updates
- 🔲 Real-time push seat availability updates
- 🔲 Connection-driven active user presence updates

### 14) System Protection Features

- ✔ Rate limiting support via RPS tracking
- ✔ Traffic throttling
- ✔ Backpressure handling through queueing
- ✔ Overload prevention mechanisms

### 15) Architecture-Level Features

- ✔ Microservice-ready modular design
- ✔ Separation of concerns (queue, booking, payment)
- ✔ Redis + DB hybrid model (speed + reliability)
- ✔ Scalable design for high traffic events

### 16) Advanced System Design Concepts Demonstrated

- ✔ Distributed locking
- ✔ Eventual consistency patterns
- ✔ High concurrency handling
- ✔ Queue-based traffic shaping
- ✔ TTL-based state management
- ✔ Failover-safe design principles
- ✔ Load-aware system behavior

## Tech Stack

- Java 17
- Spring Boot 4.0.3
- Spring Security + JWT (`jjwt`)
- Spring Data JPA
- MySQL
- Redis (Lettuce)
- Resilience4j Circuit Breaker
- Micrometer + Prometheus

## Project Structure

```text
src/main/java/com/deepak/ticketflow
├── config/        # Spring, Redis, queue, seat configuration
├── controller/    # REST controllers (auth, events, queue, booking)
├── dto/           # API request/response payloads
├── filters/       # JWT filter and authentication provider
├── handlers/      # Exception handlers and custom exceptions
├── model/         # JPA domain models
├── repository/    # Spring Data repositories
└── service/       # Business services

src/main/resources
├── application.properties
└── db/migration/V1__init.sql
```


## Core API Endpoints

### Authentication

- `POST /register` - Register a user
- `POST /login` - Login and get tokens
- `POST /refresh` - Get a new access token

### Events

- `GET /events` - Get all events
- `GET /events/{id}` - Get event details
- `POST /admin/events` - Create event (ADMIN)
- `DELETE /events/{id}` - Delete event (ADMIN)

### Queue

- `POST /api/queue/join` - Join queue / get direct booking token
- `GET /api/queue/position` - Check queue position

### Booking

- `POST /api/reservations` - Reserve seats (requires `X-Queue-Token`)
- `POST /api/bookings/confirm` - Confirm booking (requires `X-Queue-Token` + `Idempotency-Key`)

## Prerequisites

- Java 17+
- Maven 3.9+ (or use `./mvnw`)
- MySQL running on `localhost:3306`
- Redis running on `localhost:6379`

## Configuration

Default configuration in `src/main/resources/application.properties`:

- `spring.datasource.url=jdbc:mysql://localhost:3306/ticketflow`
- `spring.datasource.username=root`
- `spring.datasource.password=root`
- `spring.redis.host=localhost`
- `spring.redis.port=6379`

Update these values for your environment before running.

## Run Locally

```bash
# from project root
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

## Build and Test

```bash
./mvnw clean test
./mvnw clean package
```

## Observability

- Spring Boot Actuator is enabled via dependency.
- Prometheus registry dependency is included for metrics scraping.

## Notes

- Flyway migration scripts are under `src/main/resources/db/migration`.
- Queue logic is implemented in `service/queue` and enforced in booking endpoints.
- Booking confirmation supports idempotency with `Idempotency-Key` to avoid duplicate charges/bookings.
