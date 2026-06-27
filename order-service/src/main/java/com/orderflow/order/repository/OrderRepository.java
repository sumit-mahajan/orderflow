package com.orderflow.order.repository;

import com.orderflow.order.domain.OrderEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
  Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
