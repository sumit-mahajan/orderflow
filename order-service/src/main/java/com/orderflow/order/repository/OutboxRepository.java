package com.orderflow.order.repository;

import com.orderflow.order.domain.OutboxMessage;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

  /**
   * Polls a batch of unsent rows for the relay. PESSIMISTIC_WRITE + the SKIP_LOCKED hint
   * ({@code jakarta.persistence.lock.timeout = -2}) means concurrent relay instances grab disjoint
   * batches instead of blocking — safe horizontal scaling (architecture.mdc §8).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      "select o from OutboxMessage o where o.status = com.orderflow.order.domain.OutboxStatus.PENDING"
          + " order by o.createdAt asc")
  List<OutboxMessage> findBatchForPublishing(Pageable pageable);
}
