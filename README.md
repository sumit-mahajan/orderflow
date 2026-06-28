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

## Production triage workflow (orderId -> root cause)

When support receives an order incident, capture: `orderId`, time window (UTC), environment,
and user-facing symptom. Then use this flow:

1. **Find business outcome first (authoritative)**
   - `GET /api/orders/{orderId}` (or dashboard row detail) shows saga steps + per-step error.
   - This distinguishes "HTTP accepted" from "saga failed later."
2. **Open Zipkin and query by order tag**
   - **Zipkin UI (easiest):** in the search bar, add a **`tagQuery`** filter with value
     `order.id=<orderId>`, set lookback (e.g. Last 1 day), then click **RUN QUERY**.
     Do **not** rely on the bare `order.id=` tag chip alone — that writes the wrong URL
     param and returns recent background noise (prometheus scrapes, outbox-relay ticks)
     even though a filter appears active.
   - **Deep link:** replace `<orderId>` and open in the browser:
     ```
     http://localhost:9411/zipkin/?annotationQuery=order.id%3D<orderId>&lookback=86400000&limit=10
     ```
     (`%3D` is the URL-encoded `=`; `lookback=86400000` is 24 h in ms — widen if needed.)
   - **API / curl** (uses `annotationQuery`, not `tagQuery`):
     ```bash
     curl "http://localhost:9411/api/v2/traces?annotationQuery=order.id=<orderId>&lookback=86400000&limit=10"
     ```
   - **Expected results:** 0 traces if the order is outside the lookback window; otherwise
     one `http post /api/orders` saga trace plus a few `task outbox-relay.publish-pending`
     spans from placement time (one per outbox publish). Untagged outbox-relay / prometheus
     spans are idle infrastructure — ignore them.
   - Optional: add `serviceName=order-service` to narrow further.
3. **Follow cross-service trace path**
   - Command/event listener spans in inventory/payment/shipment are tagged with `order.id`
     and `saga.id`.
   - Outbox relay forwards persisted `traceparent` so async Kafka hops stay correlated.
4. **Use Prometheus/Grafana for blast radius**
   - Open [OrderFlow Observability](http://localhost:3000/d/orderflow-observability/orderflow-observability)
     — throughput, confirmed/failed counts, failure ratio, carrier circuit breaker.
   - Confirm if the issue is isolated vs systemic (not per-order root cause — use Zipkin step 2).

Known limit: Prometheus/Grafana are aggregate metrics and should not be used as per-order root
cause tools.

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
2. Click `Run 20 concurrent`.
3. Watch live updates in table/SSE stream and compare `Confirmed`/`Failed` counts in stat cards.

## Local verification commands

```bash
# Frontend build
cd frontend && npm install && npm run build

# Backend build/tests (skip TestContainers ITs if Docker is unavailable)
cd ..
mvn -DskipITs test
```
