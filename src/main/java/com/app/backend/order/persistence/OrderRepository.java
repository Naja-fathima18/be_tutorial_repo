package com.app.backend.order.persistence;

import com.app.backend.order.domain.Order;
import com.app.backend.order.domain.OrderStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsByIdempotencyKey(String idempotencyKey);

    @Query("select o.status from Order o where o.id = :id")
    Optional<OrderStatus> findStatusById(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Order o set o.status = :target, o.version = o.version + 1 where o.id = :id and o.status = :expected")
    int transitionStatus(
            @Param("id") Long id, @Param("expected") OrderStatus expected, @Param("target") OrderStatus target);
}
