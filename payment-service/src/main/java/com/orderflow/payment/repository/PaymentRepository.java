package com.orderflow.payment.repository;

import com.orderflow.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByOrderId(UUID orderId);
}
