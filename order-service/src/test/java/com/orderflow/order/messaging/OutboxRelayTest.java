package com.orderflow.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.order.domain.OutboxMessage;
import com.orderflow.order.domain.OutboxStatus;
import com.orderflow.order.repository.OutboxRepository;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  @Mock private OutboxRepository outbox;
  @Mock private KafkaTemplate<String, String> kafkaTemplate;
  @Mock private Tracer tracer;

  private OutboxRelay relay;

  @BeforeEach
  void setUp() {
    relay = new OutboxRelay(outbox, kafkaTemplate, new ObjectMapper(), tracer);
  }

  @Test
  void publishPending_appliesStoredHeadersAndMarksRowSent() {
    OutboxMessage row =
        new OutboxMessage(
            "ORDER",
            UUID.randomUUID(),
            "orderflow.commands.inventory",
            "order-123",
            "{\"type\":\"RESERVE_INVENTORY\"}",
            "{\"traceparent\":\"00-0123456789abcdef0123456789abcdef-89abcdef01234567-01\"}");

    when(outbox.findBatchForPublishing(any())).thenReturn(List.of(row));
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

    relay.publishPending();

    ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    ProducerRecord<String, String> sent = captor.getValue();
    assertThat(sent.headers().lastHeader("traceparent")).isNotNull();
    assertThat(new String(sent.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8))
        .isEqualTo("00-0123456789abcdef0123456789abcdef-89abcdef01234567-01");
    assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
  }
}
