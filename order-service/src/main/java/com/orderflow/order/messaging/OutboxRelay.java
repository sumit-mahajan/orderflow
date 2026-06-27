package com.orderflow.order.messaging;

import com.orderflow.order.domain.OutboxMessage;
import com.orderflow.order.repository.OutboxRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled relay that publishes PENDING outbox rows to Kafka and marks them SENT (F-07). ≈ a .NET
 * {@code BackgroundService} on a timer.
 *
 * <p>Reliability: we send synchronously and only mark SENT after the broker acks. If the process
 * dies before commit, rows stay PENDING and are re-sent (at-least-once) — consumers are idempotent,
 * so duplicates are harmless. The payload is already a JSON string, so it is sent with a String
 * serializer (no double-encoding); the consumer's JsonDeserializer parses it into a CommandMessage.
 */
@Component
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH_SIZE = 50;
  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final OutboxRepository outbox;
  private final KafkaTemplate<String, String> kafkaTemplate;

  public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafkaTemplate) {
    this.outbox = outbox;
    this.kafkaTemplate = kafkaTemplate;
  }

  @Scheduled(fixedDelayString = "${orderflow.outbox.poll-ms:1000}")
  @Transactional
  public void publishPending() {
    List<OutboxMessage> batch = outbox.findBatchForPublishing(PageRequest.of(0, BATCH_SIZE));
    for (OutboxMessage message : batch) {
      try {
        kafkaTemplate
            .send(message.getTopic(), message.getMsgKey(), message.getPayload())
            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        message.markSent(); // flushed at commit; row stays locked until then (SKIP LOCKED)
      } catch (Exception e) {
        // leave as PENDING; next poll retries (at-least-once)
        log.warn(
            "Failed to publish outbox row {} to {}: {}",
            message.getId(),
            message.getTopic(),
            e.toString());
        break;
      }
    }
  }
}
