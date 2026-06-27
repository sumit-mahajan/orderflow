package com.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Stock for one SKU. Reservation/release are done via atomic conditional UPDATEs (see {@link
 * com.orderflow.inventory.repository.InventoryItemRepository}) rather than read-modify-write, so
 * concurrent orders for the last unit are correct without optimistic-lock retry storms. The {@code
 * version} column is bumped on every change for visibility/auditing.
 */
@Entity
@Table(name = "inventory_item")
public class InventoryItem {

  @Id
  @Column(name = "sku", nullable = false, length = 64)
  private String sku;

  @Column(name = "available_qty", nullable = false)
  private int availableQty;

  @Column(name = "reserved_qty", nullable = false)
  private int reservedQty;

  @Column(name = "version", nullable = false)
  private int version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InventoryItem() {}

  public String getSku() {
    return sku;
  }

  public int getAvailableQty() {
    return availableQty;
  }

  public int getReservedQty() {
    return reservedQty;
  }

  public int getVersion() {
    return version;
  }
}
