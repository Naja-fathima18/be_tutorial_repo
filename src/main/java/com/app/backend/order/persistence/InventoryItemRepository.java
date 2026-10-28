package com.app.backend.order.persistence;

import com.app.backend.order.domain.InventoryItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Modifying
    @Query("update InventoryItem i set i.stock = i.stock - :quantity where i.id = :id and i.stock >= :quantity")
    int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("update InventoryItem i set i.stock = i.stock + :quantity where i.id = :id")
    int releaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Query("select i.stock from InventoryItem i where i.id = :id")
    Optional<Integer> findStockById(@Param("id") Long id);
}
