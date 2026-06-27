package com.orderflow.inventory.repository;

import com.orderflow.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data auto-implements this interface (no Impl class). ≈ EF Core repository.
 *
 * <p>Reserve/release use atomic conditional UPDATEs: the {@code WHERE available_qty >= :qty} clause
 * makes "decrement only if enough stock" a single row-locked statement. Concurrent reservations for
 * the last unit cannot both succeed — exactly what the concurrent-orders demo needs.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

  /**
   * @return 1 if stock was reserved, 0 if insufficient stock or the SKU does not exist.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update InventoryItem i
         set i.availableQty = i.availableQty - :qty,
             i.reservedQty  = i.reservedQty + :qty,
             i.version      = i.version + 1
       where i.sku = :sku and i.availableQty >= :qty
      """)
  int tryReserve(@Param("sku") String sku, @Param("qty") int qty);

  /**
   * @return rows affected (1 if the SKU exists).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update InventoryItem i
         set i.availableQty = i.availableQty + :qty,
             i.reservedQty  = i.reservedQty - :qty,
             i.version      = i.version + 1
       where i.sku = :sku
      """)
  int releaseStock(@Param("sku") String sku, @Param("qty") int qty);
}
