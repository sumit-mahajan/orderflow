package com.orderflow.order.service;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-order distributed lock in Redis (D-08). Guards saga state transitions so two instances/threads
 * can't advance the same order concurrently. This reduces contention/wasted work; the real
 * correctness backstop is the {@code @Version} optimistic lock on {@code saga_instance}.
 *
 * <p>≈ .NET: a RedLock-style lock around the critical section, with rowversion concurrency behind it.
 */
@Component
public class SagaLock {

  private static final Duration LOCK_TTL = Duration.ofSeconds(10);
  private static final int MAX_ATTEMPTS = 5;
  private static final long RETRY_SLEEP_MS = 100;

  private final StringRedisTemplate redis;

  public SagaLock(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public boolean acquireWithRetry(UUID orderId, String token) {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      Boolean ok = redis.opsForValue().setIfAbsent(key(orderId), token, LOCK_TTL);
      if (Boolean.TRUE.equals(ok)) {
        return true;
      }
      sleep();
    }
    return false;
  }

  /** Best-effort compare-and-delete so we only release a lock we still own. */
  public void unlock(UUID orderId, String token) {
    String current = redis.opsForValue().get(key(orderId));
    if (token.equals(current)) {
      redis.delete(key(orderId));
    }
  }

  private static String key(UUID orderId) {
    return "lock:saga:" + orderId;
  }

  private static void sleep() {
    try {
      Thread.sleep(RETRY_SLEEP_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
