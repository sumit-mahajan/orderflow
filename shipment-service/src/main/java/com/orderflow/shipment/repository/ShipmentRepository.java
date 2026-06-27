package com.orderflow.shipment.repository;

import com.orderflow.shipment.domain.Shipment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

  Optional<Shipment> findByOrderId(UUID orderId);

  boolean existsByOrderId(UUID orderId);
}
