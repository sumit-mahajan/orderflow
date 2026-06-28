# OrderFlow

Distributed order-fulfillment demo using a saga orchestrator (`order-service`) with compensating transactions across inventory, payment, and shipment services.

## Start the full stack

```bash
docker compose up --build
```

Services:

- Dashboard: [http://localhost:8088](http://localhost:8088)
- Order API: [http://localhost:8080/api/orders](http://localhost:8080/api/orders)
- Zipkin: [http://localhost:9411](http://localhost:9411)
- Prometheus: [http://localhost:9090](http://localhost:9090)
- Grafana: [http://localhost:3000](http://localhost:3000) (default `admin` / `admin`)
- Grafana dashboard (after login): [OrderFlow Observability](http://localhost:3000/d/orderflow-observability/orderflow-observability)

## Architecture

Four Spring Boot microservices coordinate a **3-step saga** (inventory → payment → shipment)
using **orchestration**, not choreography. Only `order-service` owns saga state and
compensation logic; the other services are single-purpose participants.

```
React dashboard ──REST/SSE──► order-service (orchestrator)
                                  │
                    commands      │  result events
                    (Kafka)       ▲  (Kafka)
        ┌─────────────┬───────────┴───────────┐
        ▼             ▼                       ▼
 inventory-service  payment-service  shipment-service
```

**Communication:** services talk **only through Kafka** — no direct HTTP between them.
Commands go to `orderflow.commands.*`; results publish to `orderflow.events.*`, keyed by
`orderId` for per-order ordering.

**Reliability patterns:**
- **Transactional outbox** — saga state and outbound commands commit together; a relay
  poller publishes to Kafka (at-least-once delivery).
- **Idempotent consumers** — safe redelivery on every participant service.
- **Compensating transactions** — failed steps trigger reverse-order rollback
  (release inventory, refund payment, cancel shipment).
- **Redis distributed lock + Postgres optimistic locking** — guards concurrent saga
  transitions on the same order.
- **Resilience4j circuit breaker** — on the mock carrier in `shipment-service`; open
  breaker fast-fails and drives saga compensation.

**Data:** one Postgres instance with four isolated schemas (`order`, `inventory`, `payment`,
`shipment`), each accessed by a dedicated DB user.

**Observability:** OpenTelemetry traces export to Zipkin (W3C `traceparent` propagated
across Kafka hops); Micrometer metrics scrape into Prometheus; Grafana dashboards show
throughput, success/failure rates, and circuit-breaker state.

## Tech stack

| Layer | Technologies |
|---|---|
| **Backend** | Java 21 · Spring Boot 3.3 · Maven multi-module monorepo |
| **Services** | `order-service` (orchestrator + REST + SSE) · `inventory-service` · `payment-service` · `shipment-service` · shared `common` module |
| **Messaging** | Apache Kafka 3.8 (KRaft, single broker) |
| **Persistence** | PostgreSQL 16 · Spring Data JPA · Flyway migrations |
| **Caching / locks** | Redis 7 (idempotency keys, saga transition locks) |
| **Resilience** | Resilience4j (circuit breaker on shipment carrier) |
| **Frontend** | React 19 · TypeScript · Vite 8 · Tailwind CSS · SSE live updates |
| **Observability** | OpenTelemetry · Micrometer · Zipkin · Prometheus · Grafana |
| **Testing** | JUnit 5 · Testcontainers (Postgres, Kafka, Redis integration tests) |
| **Runtime** | Docker Compose (full local stack — no cloud dependencies) |

## Demo script (F-16)

All scenarios can be run from the dashboard controls at `http://localhost:8088`.

### 1) Happy path

1. Keep `Force payment fail` unchecked.
2. Keep `Shipment mode = ok`.
3. Submit an order.
4. Verify row flow: `Inventory Success` -> `Payment Success` -> `Shipment Success` -> `OrderConfirmed`.

### 2) Payment failure -> inventory released

1. Enable `Force payment fail`.
2. Submit an order.
3. Verify row flow: payment fails, saga enters `Compensating`, inventory compensation succeeds, final state `OrderFailed`.

### 3) Shipment down/hang -> breaker -> compensation

1. Set `Shipment mode = hang` (or `fail`).
2. Submit 3+ orders to trigger the Resilience4j carrier breaker.
3. Verify failures move through `Compensating` with `Payment` compensated then `Inventory` compensated.
4. In Grafana, check **Carrier Circuit Breaker** panel transitions to OPEN.
5. Switch back to `Shipment mode = ok`; after open-window timeout, new orders recover.

### 4) Concurrent load (~20 orders)

1. Set `Shipment mode = ok` and disable failure toggles.
2. Click `Run 20 concurrent` (uses `SKU-LIMITED`, 3 units — expect partial success + inventory failures).
3. Watch live updates in table/SSE stream and compare `Confirmed`/`Failed` counts in stat cards.

Default single-order flows use `SKU-LAPTOP` (50 units). Use the SKU dropdown for `SKU-PHONE` or scarce `SKU-LIMITED`.

## Local verification commands

```bash
# Frontend build
cd frontend && npm install && npm run build

# Backend build/tests (skip TestContainers ITs if Docker is unavailable)
cd ..
mvn -DskipITs test
```
