package com.orderflow.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.order.domain.OutboxMessage;
import com.orderflow.order.repository.OutboxRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

  @Mock private OutboxRepository outbox;
  @Mock private Tracer tracer;
  @Mock private Span span;
  @Mock private TraceContext traceContext;

  private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
  private OutboxWriter writer;

  @BeforeEach
  void setUp() {
    writer = new OutboxWriter(outbox, json, tracer);
  }

  @Test
  void enqueueCommand_withActiveSpan_persistsTraceparentHeader() throws Exception {
    when(tracer.currentSpan()).thenReturn(span);
    when(span.context()).thenReturn(traceContext);
    when(traceContext.traceId()).thenReturn("0123456789abcdef0123456789abcdef");
    when(traceContext.spanId()).thenReturn("89abcdef01234567");
    when(traceContext.sampled()).thenReturn(true);

    CommandMessage command =
        new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            CommandType.RESERVE_INVENTORY,
            null,
            null,
            null,
            Instant.now());

    writer.enqueueCommand("orderflow.commands.inventory", command);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).save(captor.capture());
    Map<String, String> headers =
        json.readValue(captor.getValue().getHeaders(), new TypeReference<Map<String, String>>() {});
    assertThat(headers.get("traceparent"))
        .isEqualTo("00-0123456789abcdef0123456789abcdef-89abcdef01234567-01");
  }

  @Test
  void enqueueCommand_withoutSpan_persistsNullHeaders() {
    when(tracer.currentSpan()).thenReturn(null);

    CommandMessage command =
        new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            CommandType.RESERVE_INVENTORY,
            null,
            null,
            null,
            Instant.now());

    writer.enqueueCommand("orderflow.commands.inventory", command);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).save(captor.capture());
    assertThat(captor.getValue().getHeaders()).isNull();
  }
}
