# GoLive Backend

A low-latency live streaming backend built with Spring Boot.
A streamer clicks **Go Live**, shares a link — viewers watch instantly.
No accounts required.

---

## Architecture

**Modular Monolith** — all modules live in one deployable JAR but maintain
strict boundaries. Modules communicate through service interfaces,
never by reaching into each other's repositories.

```
golive-backend/
├── stream/        → core domain: stream lifecycle, CRUD
├── livekit/       → media token generation (host + viewer)
├── chat/          → real-time WebSocket/STOMP chat
├── config/        → CORS, WebSocket broker configuration
├── common/        → shared exceptions and utilities
└── database/      → DataSource, JPA, transaction configuration
```

**Stack**

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL (HikariCP connection pool) |
| Media | LiveKit (WebRTC screen sharing) |
| Real-time chat | WebSocket + STOMP |
| Build | Maven |

**Key design decisions**

- No authentication for MVP — host identity proved via a secret `hostKey`
- Stream state machine enforced on the entity: `CREATED → LIVE → ENDED`
- All timestamps stored as UTC `Instant` / PostgreSQL `TIMESTAMPTZ`
- Secrets never hardcoded — injected via environment variables

---

## Local Setup

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 15+ (running locally)
- A LiveKit account — [cloud.livekit.io](https://cloud.livekit.io)

### 1. Clone the repository

```bash
git clone https://github.com/your-org/golive-backend.git
cd golive-backend
```

### 2. Create the local database

Open pgAdmin or psql and run:

```sql
CREATE DATABASE golive_dev;
```

### 3. Create your local config file

```bash
cp src/main/resources/application-local.yml.example \
   src/main/resources/application-local.yml
```

Open `application-local.yml` and fill in:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/golive_dev
    username: YOUR_POSTGRES_USERNAME
    password: YOUR_POSTGRES_PASSWORD

livekit:
  url: wss://your-project.livekit.cloud
  api-key: YOUR_LIVEKIT_API_KEY
  api-secret: YOUR_LIVEKIT_API_SECRET

cors:
  allowed-origins: http://localhost:3000
```

> ⚠️ `application-local.yml` is gitignored. Never commit it.

### 4. Start the application

```bash
mvn spring-boot:run
```

### 5. Verify it's running

```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "golive-backend",
  "timestamp": "2024-01-15T13:30:00.000Z"
}
```

---

## API Reference

### Health

| Method | Path | Description |
|---|---|---|
| GET | `/api/health` | Application health check |

### Streams

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/streams` | None | Create a new stream |
| GET | `/api/streams/{id}` | None | Get stream details |
| GET | `/api/streams?status=LIVE` | None | List live streams |
| PATCH | `/api/streams/{id}/start` | `X-Host-Key` header | Start streaming |
| PATCH | `/api/streams/{id}/end` | `X-Host-Key` header | End stream |

### LiveKit Tokens

| Method | Path | Description |
|---|---|---|
| POST | `/api/livekit/token` | Generate host or viewer token |

### Chat (WebSocket)

| Type | Destination | Description |
|---|---|---|
| CONNECT | `/api/ws` | Open WebSocket connection |
| SUBSCRIBE | `/topic/streams/{streamId}/chat` | Receive chat messages |
| SEND | `/app/chat/send` | Send a chat message |

---

## Git Workflow

We use feature branches and pull requests:

```bash
# Start a new feature
git checkout -b feat/your-feature-name

# Commit with conventional commits
git commit -m "feat(stream): add stream creation endpoint"

# Push and open a PR for review
git push origin feat/your-feature-name
```

**Commit types:** `feat` `fix` `refactor` `chore` `docs`

---

## Environment Variables (Production)

| Variable | Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Set to `prod` |
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | DB username |
| `DATABASE_PASSWORD` | DB password |
| `LIVEKIT_URL` | LiveKit server WSS URL |
| `LIVEKIT_API_KEY` | LiveKit API key |
| `LIVEKIT_API_SECRET` | LiveKit API secret |
| `FRONTEND_URL` | Frontend origin for CORS |

---

## Running Tests

```bash
mvn test
```