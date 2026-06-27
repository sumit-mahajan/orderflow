package com.orderflow.inventory.service;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.LineItem;
import com.orderflow.inventory.domain.InventoryReservation;
import com.orderflow.inventory.domain.ReservationStatus;
import com.orderflow.inventory.messaging.Events;
import com.orderflow.inventory.repository.InventoryItemRepository;
import com.orderflow.inventory.repository.InventoryReservationRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserve / release stock. Both operations are idempotent (F-04 / F-05):
 *
 * <ul>
 *   <li><b>reserve</b>: if reservations already exist for the order, treat as a replay and return
 *       success without touching stock again. Otherwise decrement each line atomically; if any line
 *       lacks stock, throw so the whole transaction rolls back (all-or-nothing).
 *   <li><b>release</b>: only RESERVED rows are released; a second release finds none and is a no-op.
 * </ul>
 */
@Service
public class InventoryService {

  private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

  private final InventoryItemRepository items;
  private final InventoryReservationRepository reservations;

  public InventoryService(
      InventoryItemRepository items, InventoryReservationRepository reservations) {
    this.items = items;
    this.reservations = reservations;
  }

  @Transactional
  public EventMessage reserve(CommandMessage cmd) {
    UUID orderId = cmd.correlationId();

    List<InventoryReservation> existing = reservations.findByOrderId(orderId);
    if (!existing.isEmpty()) {
      log.info("Reserve for order {} already processed — idempotent replay", orderId);
      return Events.reserved(cmd);
    }

    for (LineItem line : cmd.items()) {
      int updated = items.tryReserve(line.sku(), line.qty());
      if (updated == 0) {
        // rolls back any lines reserved earlier in this transaction
        throw new InsufficientStockException(line.sku());
      }
      reservations.save(new InventoryReservation(orderId, line.sku(), line.qty()));
    }
    log.info("Reserved {} line(s) for order {}", cmd.items().size(), orderId);
    return Events.reserved(cmd);
  }

  @Transactional
  public EventMessage release(CommandMessage cmd) {
    UUID orderId = cmd.correlationId();
    List<InventoryReservation> reserved =
        reservations.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);

    for (InventoryReservation r : reserved) {
      items.releaseStock(r.getSku(), r.getQty());
      r.markReleased();
      reservations.save(r);
    }
    log.info("Released {} reservation(s) for order {} (idempotent)", reserved.size(), orderId);
    return Events.released(cmd);
  }
}
