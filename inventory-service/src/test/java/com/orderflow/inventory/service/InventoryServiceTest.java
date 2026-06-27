package com.orderflow.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import com.orderflow.common.messaging.LineItem;
import com.orderflow.inventory.domain.InventoryReservation;
import com.orderflow.inventory.domain.ReservationStatus;
import com.orderflow.inventory.repository.InventoryItemRepository;
import com.orderflow.inventory.repository.InventoryReservationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

  @Mock private InventoryItemRepository items;
  @Mock private InventoryReservationRepository reservations;
  @InjectMocks private InventoryService service;

  private static CommandMessage reserveCmd(UUID orderId, String sku, int qty) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        CommandType.RESERVE_INVENTORY,
        List.of(new LineItem(sku, qty)),
        null,
        null,
        Instant.now());
  }

  @Test
  void reserve_success_decrementsAndSavesReservation() {
    UUID orderId = UUID.randomUUID();
    when(reservations.findByOrderId(orderId)).thenReturn(List.of());
    when(items.tryReserve("SKU-LAPTOP", 2)).thenReturn(1);

    EventMessage event = service.reserve(reserveCmd(orderId, "SKU-LAPTOP", 2));

    assertThat(event.type()).isEqualTo(EventType.INVENTORY_RESERVED);
    verify(items).tryReserve("SKU-LAPTOP", 2);
    verify(reservations).save(any(InventoryReservation.class));
  }

  @Test
  void reserve_idempotentReplay_doesNotTouchStock() {
    UUID orderId = UUID.randomUUID();
    when(reservations.findByOrderId(orderId))
        .thenReturn(List.of(new InventoryReservation(orderId, "SKU-LAPTOP", 2)));

    EventMessage event = service.reserve(reserveCmd(orderId, "SKU-LAPTOP", 2));

    assertThat(event.type()).isEqualTo(EventType.INVENTORY_RESERVED);
    verify(items, never()).tryReserve(anyString(), anyInt());
  }

  @Test
  void reserve_insufficientStock_throwsToRollBack() {
    UUID orderId = UUID.randomUUID();
    when(reservations.findByOrderId(orderId)).thenReturn(List.of());
    when(items.tryReserve("SKU-LIMITED", 99)).thenReturn(0);

    assertThatThrownBy(() -> service.reserve(reserveCmd(orderId, "SKU-LIMITED", 99)))
        .isInstanceOf(InsufficientStockException.class);
  }

  @Test
  void release_idempotent_whenNothingReserved_isNoOp() {
    UUID orderId = UUID.randomUUID();
    when(reservations.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED))
        .thenReturn(List.of());

    EventMessage event = service.release(reserveCmd(orderId, "SKU-LAPTOP", 2));

    assertThat(event.type()).isEqualTo(EventType.INVENTORY_RELEASED);
    verify(items, never()).releaseStock(anyString(), anyInt());
  }

  @Test
  void release_restoresStockForReservedRows() {
    UUID orderId = UUID.randomUUID();
    when(reservations.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED))
        .thenReturn(List.of(new InventoryReservation(orderId, "SKU-LAPTOP", 2)));
    when(items.releaseStock(eq("SKU-LAPTOP"), eq(2))).thenReturn(1);

    EventMessage event = service.release(reserveCmd(orderId, "SKU-LAPTOP", 2));

    assertThat(event.type()).isEqualTo(EventType.INVENTORY_RELEASED);
    verify(items, times(1)).releaseStock("SKU-LAPTOP", 2);
  }
}
