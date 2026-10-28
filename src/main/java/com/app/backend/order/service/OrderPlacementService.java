package com.app.backend.order.service;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.order.domain.InsufficientStockException;
import com.app.backend.order.domain.InventoryItem;
import com.app.backend.order.domain.Order;
import com.app.backend.order.persistence.InventoryItemRepository;
import com.app.backend.order.persistence.OrderRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPlacementService {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacementService.class);

    private final OrderRepository orderRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public OrderPlacementService(OrderRepository orderRepository, InventoryItemRepository inventoryItemRepository) {
        this.orderRepository = orderRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Transactional
    public OrderDetails placeNew(String idempotencyKey, String requestHash, PlaceOrderCommand command) {
        List<PlaceOrderCommand.Line> lines = command.lines().stream()
                .sorted(Comparator.comparing(PlaceOrderCommand.Line::productId))
                .toList();
        Map<Long, InventoryItem> products = loadProducts(lines);

        Order order = new Order(command.customerId(), idempotencyKey, requestHash);
        lines.forEach(line -> order.addItem(products.get(line.productId()), line.quantity()));
        Order saved = orderRepository.saveAndFlush(order);

        lines.forEach(line -> reserve(products.get(line.productId()), line.quantity()));
        log.info("Placed order {} with {} lines", saved.getId(), lines.size());
        return OrderDetails.from(saved);
    }

    @Transactional(readOnly = true)
    public Optional<StoredOrder> findByIdempotencyKey(String idempotencyKey) {
        return orderRepository
                .findWithItemsByIdempotencyKey(idempotencyKey)
                .map(order -> new StoredOrder(order.getRequestHash(), OrderDetails.from(order)));
    }

    private Map<Long, InventoryItem> loadProducts(List<PlaceOrderCommand.Line> lines) {
        List<Long> productIds =
                lines.stream().map(PlaceOrderCommand.Line::productId).toList();
        Map<Long, InventoryItem> products = inventoryItemRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));
        for (Long productId : productIds) {
            if (!products.containsKey(productId)) {
                throw new NotFoundException("Product %d not found".formatted(productId));
            }
        }
        return products;
    }

    private void reserve(InventoryItem product, int quantity) {
        int updated = inventoryItemRepository.reserveStock(product.getId(), quantity);
        if (updated == 0) {
            int available =
                    inventoryItemRepository.findStockById(product.getId()).orElse(0);
            throw new InsufficientStockException(product.getId(), product.getName(), quantity, available);
        }
    }
}
