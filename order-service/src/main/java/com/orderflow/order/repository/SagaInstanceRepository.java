package com.orderflow.order.repository;

import com.orderflow.order.domain.SagaInstance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
  Optional<SagaInstance> findByOrderId(UUID orderId);
}
