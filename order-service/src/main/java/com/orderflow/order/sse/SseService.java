package com.orderflow.order.sse;

import com.orderflow.order.service.OrderQueryService;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live dashboard transport (D-11). Holds open SSE connections and pushes a full order snapshot on
 * every saga transition. Single-instance fan-out for M1; multi-instance fan-out (each instance
 * consuming the saga-events topic) is the documented scale-up path (architecture.mdc §8).
 */
@Service
public class SseService {

  private static final Logger log = LoggerFactory.getLogger(SseService.class);

  private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
  private final OrderQueryService query;

  public SseService(OrderQueryService query) {
    this.query = query;
  }

  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(0L); // no timeout
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(e -> emitters.remove(emitter));
    return emitter;
  }

  public void publishSnapshot(UUID orderId) {
    Object snapshot;
    try {
      snapshot = query.detail(orderId);
    } catch (RuntimeException e) {
      log.warn("Could not build SSE snapshot for order {}: {}", orderId, e.toString());
      return;
    }
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name("order-update").data(snapshot));
      } catch (IOException | IllegalStateException e) {
        emitters.remove(emitter);
      }
    }
  }
}
