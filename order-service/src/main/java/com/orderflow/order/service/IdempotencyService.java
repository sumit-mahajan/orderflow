package com.orderflow.order.service;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Idempotent order submission (F-02) backed by Redis. The {@code SET NX} ({@code setIfAbsent}) is the
 * atomic gate: only the first caller for a key "acquires" and proceeds to create the order; later
 * callers with the same body get the original orderId, and a different body for the same key is a
 * conflict (409). The UNIQUE constraint on {@code orders.idempotency_key} is the durable backstop.
 *
 * <p>Stored value format: {@code <orderId>::<bodyHash>}.
 */
@Service
public class IdempotencyService {

  private static final Duration TTL = Duration.ofHours(24);

  private final StringRedisTemplate redis;

  public IdempotencyService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /** Outcome of trying to claim an idempotency key. */
  public record Acquire(boolean acquired, UUID existingOrderId) {
    static Acquire claimed() {
      return new Acquire(true, null);
    }

    static Acquire duplicate(UUID existingOrderId) {
      return new Acquire(false, existingOrderId);
    }
  }

  public Acquire acquire(String key, UUID candidateOrderId, String bodyHash) {
    String value = candidateOrderId + "::" + bodyHash;
    Boolean ok = redis.opsForValue().setIfAbsent(redisKey(key), value, TTL);
    if (Boolean.TRUE.equals(ok)) {
      return Acquire.claimed();
    }

    String stored = redis.opsForValue().get(redisKey(key));
    if (stored == null) {
      // expired between the SET attempt and the GET — try once more to claim it
      Boolean retry = redis.opsForValue().setIfAbsent(redisKey(key), value, TTL);
      if (Boolean.TRUE.equals(retry)) {
        return Acquire.claimed();
      }
      stored = redis.opsForValue().get(redisKey(key));
      if (stored == null) {
        return Acquire.claimed();
      }
    }

    String[] parts = stored.split("::", 2);
    String existingHash = parts.length > 1 ? parts[1] : "";
    if (!existingHash.equals(bodyHash)) {
      throw new IdempotencyConflictException(key);
    }
    return Acquire.duplicate(UUID.fromString(parts[0]));
  }

  /** Re-point a key at a new orderId (used to self-heal a slot whose owner rolled back). */
  public void overwrite(String key, UUID orderId, String bodyHash) {
    redis.opsForValue().set(redisKey(key), orderId + "::" + bodyHash, TTL);
  }

  private static String redisKey(String key) {
    return "idem:" + key;
  }
}
