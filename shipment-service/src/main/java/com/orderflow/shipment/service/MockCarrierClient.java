package com.orderflow.shipment.service;

import com.orderflow.common.messaging.Simulate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulates an external carrier API (F-14). Three modes controlled by {@link Simulate#shipmentMode()}:
 *
 * <ul>
 *   <li><b>ok</b> (default) — returns a mock label ref immediately.
 *   <li><b>fail</b> — throws immediately; the circuit breaker records a failure.
 *   <li><b>hang</b> — sleeps for 3 s then throws; slow-call threshold trips the breaker after
 *       several hangs.
 * </ul>
 *
 * The 3-second hang is short enough to be demo-friendly while still illustrating the slow-call
 * breaker behaviour.
 */
@Component
public class MockCarrierClient {

  private static final Logger log = LoggerFactory.getLogger(MockCarrierClient.class);

  public String createLabel(UUID orderId, Simulate simulate) {
    String mode = (simulate != null && simulate.shipmentMode() != null) ? simulate.shipmentMode() : "ok";

    return switch (mode) {
      case "fail" -> {
        log.warn("MockCarrier: injected failure for order {}", orderId);
        throw new CarrierException("Carrier rejected shipment (injected failure)");
      }
      case "hang" -> {
        log.warn("MockCarrier: hanging for order {} — will timeout", orderId);
        try {
          Thread.sleep(3_000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        throw new CarrierException("Carrier timeout (hang mode)");
      }
      default -> {
        String ref = "LABEL-" + orderId.toString().substring(0, 8).toUpperCase();
        log.info("MockCarrier: label {} created for order {}", ref, orderId);
        yield ref;
      }
    };
  }
}
