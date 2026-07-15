# slot-api-gateway

**Spring Cloud Gateway MVC** — edge API gateway for the `slot-central` microservices platform.

This service is part of the re-architecture of the [`slot-central-server-express-rmq`](https://github.com/wilsonks/slot-central-server-express-rmq) Node.js EGM (Electronic Gaming Machine) slot-floor backend into Spring Boot microservices.

## Platform architecture

This gateway is service #1 in the following microservices platform:

| Service | Repository | Responsibility |
|---------|-----------|----------------|
| **slot-api-gateway** ← _this repo_ | `wilsonks/slot-api-gateway` | Edge routing, auth filter, rate limiting, correlation IDs |
| slot-auth-service | `wilsonks/slot-auth-service` | Staff + player/card authentication, JWT issuance |
| slot-game-controller-service | `wilsonks/slot-game-controller-service` | Spin orchestration, session management |
| slot-game-engine-service | `wilsonks/slot-game-engine-service` | RNG, reels, paytables, primary/secondary game math |
| slot-bank-service | `wilsonks/slot-bank-service` | Wallet/ledger (deposit, withdraw, buy-in, buy-out) |
| slot-floor-management-service | `wilsonks/slot-floor-management-service` | EGM/station registry, RabbitMQ gateway to physical machines |
| slot-jackpot-service | `wilsonks/slot-jackpot-service` | Progressive jackpot pool state |
| slot-config-service | `wilsonks/slot-config-service` | Game config / feature flags |
| slot-logging-service | `wilsonks/slot-logging-service` | Audit/log trail |

## Routing

All downstream requests are forwarded with their **full path preserved** — no path prefix stripping is applied. For example:

```
Client: GET /api/config/features
Gateway forwards: GET /api/config/features → slot-config-service
```

Each downstream service is therefore expected to handle the `/api/{service}/` prefix in its own route definitions.

| Route prefix | Downstream service | Env variable | Default |
|---|---|---|---|
| `/api/auth/**` | slot-auth-service | `AUTH_SERVICE_URL` | `http://localhost:8081` |
| `/api/game-controller/**` | slot-game-controller-service | `GAME_CONTROLLER_SERVICE_URL` | `http://localhost:8082` |
| `/api/game-engine/**` | slot-game-engine-service | `GAME_ENGINE_SERVICE_URL` | `http://localhost:8083` |
| `/api/bank/**` | slot-bank-service | `BANK_SERVICE_URL` | `http://localhost:8084` |
| `/api/floor/**` | slot-floor-management-service | `FLOOR_SERVICE_URL` | `http://localhost:8085` |
| `/api/jackpot/**` | slot-jackpot-service | `JACKPOT_SERVICE_URL` | `http://localhost:8086` |
| `/api/config/**` | slot-config-service | `CONFIG_SERVICE_URL` | `http://localhost:8087` |
| `/api/logs/**` | slot-logging-service | `LOGGING_SERVICE_URL` | `http://localhost:8088` |

## Cross-cutting concerns

### Correlation ID (`CorrelationIdFilter`)
Every request is assigned a `X-Correlation-Id` header. If the client sends one, it is propagated; otherwise a new UUID is generated. The correlation ID is included in all log lines via MDC and echoed in all error response bodies.

### JWT authentication (`JwtAuthenticationFilter`)
All routes except `/api/auth/**` and `/actuator/health` require a `Bearer` token in the `Authorization` header. The filter performs **structural validation** (verifies the token has three Base64url-encoded parts), which is sufficient for this phase. Real signature verification is left as a pluggable extension point via the `JwtValidator` interface.

> **TODO**: integrate real JWT signature verification once `slot-auth-service` publishes its JWKS endpoint.

### Rate limiting (`RateLimitingFilter`)
In-memory token-bucket rate limiting: 20-token bucket, refilling at 10 tokens/second, keyed by `X-Api-Key` header (if present) or remote IP. Returns `429 Too Many Requests` when the bucket is empty.

> **TODO**: replace with Redis-backed rate limiting for horizontal scaling.

## Running locally

### Prerequisites
- Java 21
- Docker (optional, for container-based runs)

### `./gradlew bootRun`
```bash
./gradlew bootRun
# Gateway starts on http://localhost:8080
# Downstream services are expected on localhost:8081-8088 (or override via env vars)
```

### Via Docker Compose
```bash
docker-compose up --build
# Gateway exposed on http://localhost:8080
# Edit docker-compose.yml to point downstream service URLs at your running services
```

### Running tests
```bash
./gradlew test
# Test report: build/reports/tests/test/index.html
```

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `AUTH_SERVICE_URL` | `http://localhost:8081` | slot-auth-service base URL |
| `GAME_CONTROLLER_SERVICE_URL` | `http://localhost:8082` | slot-game-controller-service base URL |
| `GAME_ENGINE_SERVICE_URL` | `http://localhost:8083` | slot-game-engine-service base URL |
| `BANK_SERVICE_URL` | `http://localhost:8084` | slot-bank-service base URL |
| `FLOOR_SERVICE_URL` | `http://localhost:8085` | slot-floor-management-service base URL |
| `JACKPOT_SERVICE_URL` | `http://localhost:8086` | slot-jackpot-service base URL |
| `CONFIG_SERVICE_URL` | `http://localhost:8087` | slot-config-service base URL |
| `LOGGING_SERVICE_URL` | `http://localhost:8088` | slot-logging-service base URL |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | Set to `docker` or `prod` to activate the respective profile |

## Observability

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Health check (public) |
| `GET /actuator/info` | Build info |
| `GET /actuator/gateway/routes` | Active gateway routes |
| `GET /actuator/prometheus` | Prometheus metrics scrape endpoint |

All log output is structured JSON (via logstash-logback-encoder) with `correlationId` included in every line.

## Known TODOs / next steps

- **Real JWT signature verification**: Once `slot-auth-service` is live and publishes a JWKS endpoint, replace the structural stub in `JwtAuthenticationFilter.validate()` with a real JWKS-backed verifier. The `JwtValidator` interface is the extension point.
- **Redis-backed rate limiting**: The current in-memory token bucket does not survive restarts and is not shared across gateway instances. Replace with Redis + Spring Data Redis or Spring Cloud Gateway's built-in Redis rate limiter for production.
- **Service discovery**: Static env-var routing is intentional for this phase. Once more services are stable, introduce Consul or Eureka + Spring Cloud LoadBalancer to replace static URLs.
- **mTLS between gateway and downstream services**: For production casino-floor deployment, all internal service-to-service traffic should be mTLS.
- **WebSocket proxying**: The original monolith included a WebSocket server for real-time topper/machine events. This will need to be re-added once `slot-floor-management-service` is built (Spring Cloud Gateway MVC supports WebSocket proxying via `spring-cloud-starter-gateway-mvc`).
