package com.orderflow.inventory.repository;

import com.orderflow.inventory.domain.InventoryReservation;
import com.orderflow.inventory.domain.ReservationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository
    extends JpaRepository<InventoryReservation, UUID> {

  List<InventoryReservation> findByOrderId(UUID orderId);

  List<InventoryReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);
}
