package com.app.backend.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.order.domain.InventoryItem;
import com.app.backend.order.domain.Order;
import com.app.backend.order.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class OrderPersistenceTest {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long insertProduct(String sku, int stock) {
        jdbcTemplate.update(
                "INSERT INTO inventory_items (sku, name, price, stock) VALUES (?, ?, ?, ?)", sku, sku, 5, stock);
        return jdbcTemplate.queryForObject("SELECT id FROM inventory_items WHERE sku = ?", Long.class, sku);
    }

    @Test
    void seedsInventoryItems() {
        assertThat(inventoryItemRepository.count()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void reservesStockOnlyWhenEnoughIsAvailable() {
        long productId = insertProduct("P-RESERVE", 3);

        int exact = inventoryItemRepository.reserveStock(productId, 3);
        int tooMany = inventoryItemRepository.reserveStock(productId, 1);

        assertThat(exact).isEqualTo(1);
        assertThat(tooMany).isZero();
        assertThat(inventoryItemRepository.findStockById(productId)).contains(0);
    }

    @Test
    void releasesStock() {
        long productId = insertProduct("P-RELEASE", 2);

        int updated = inventoryItemRepository.releaseStock(productId, 4);

        assertThat(updated).isEqualTo(1);
        assertThat(inventoryItemRepository.findStockById(productId)).contains(6);
    }

    @Test
    void reportsNoStockForUnknownProduct() {
        assertThat(inventoryItemRepository.findStockById(-1L)).isEmpty();
    }

    @Test
    void persistsOrderWithItemsAndLooksItUpByIdempotencyKey() {
        long productId = insertProduct("P-ORDER", 10);
        InventoryItem product = inventoryItemRepository.findById(productId).orElseThrow();
        Order order = new Order(7L, "key-persist", "hash");
        order.addItem(product, 3);
        orderRepository.saveAndFlush(order);
        entityManager.clear();

        Order found =
                orderRepository.findWithItemsByIdempotencyKey("key-persist").orElseThrow();

        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(found.getTotal()).isEqualByComparingTo("15.00");
        assertThat(found.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(productId);
            assertThat(item.getQuantity()).isEqualTo(3);
        });
    }

    @Test
    void transitionsStatusOnlyFromTheExpectedStatus() {
        Order saved = orderRepository.saveAndFlush(new Order(7L, "key-transition", "hash"));

        int first = orderRepository.transitionStatus(saved.getId(), OrderStatus.PLACED, OrderStatus.CANCELLED);
        int second = orderRepository.transitionStatus(saved.getId(), OrderStatus.PLACED, OrderStatus.CANCELLED);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(orderRepository.findStatusById(saved.getId())).contains(OrderStatus.CANCELLED);
    }
}
