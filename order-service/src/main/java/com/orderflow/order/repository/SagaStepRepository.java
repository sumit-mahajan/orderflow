package com.orderflow.order.repository;

import com.orderflow.order.domain.SagaStepEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStepRepository extends JpaRepository<SagaStepEntity, UUID> {
  List<SagaStepEntity> findBySagaIdOrderByCreatedAtAsc(UUID sagaId);
}
