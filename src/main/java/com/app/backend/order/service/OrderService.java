package com.app.backend.order.service;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.order.domain.IdempotencyKeyReusedException;
import com.app.backend.order.domain.Order;
import com.app.backend.order.domain.OrderItem;
import com.app.backend.order.domain.OrderNotCancellableException;
import com.app.backend.order.domain.OrderStatus;
import com.app.backend.order.persistence.InventoryItemRepository;
import com.app.backend.order.persistence.OrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderPlacementService orderPlacementService;
    private final OrderRepository orderRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public OrderService(
            OrderPlacementService orderPlacementService,
            OrderRepository orderRepository,
            InventoryItemRepository inventoryItemRepository) {
        this.orderPlacementService = orderPlacementService;
        this.orderRepository = orderRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    public PlacementResult placeOrder(String idempotencyKey, PlaceOrderCommand command) {
        rejectDuplicateProducts(command);
        String requestHash = fingerprint(command);
        return findReplay(idempotencyKey, requestHash)
                .orElseGet(() -> placeOrReplayWinner(idempotencyKey, requestHash, command));
    }

    @Transactional(readOnly = true)
    public OrderDetails getOrder(Long id) {
        return orderRepository.findWithItemsById(id).map(OrderDetails::from).orElseThrow(() -> orderNotFound(id));
    }

    @Transactional
    public OrderDetails cancelOrder(Long id) {
        int updated = orderRepository.transitionStatus(id, OrderStatus.PLACED, OrderStatus.CANCELLED);
        if (updated == 0) {
            OrderStatus current = orderRepository.findStatusById(id).orElseThrow(() -> orderNotFound(id));
            throw new OrderNotCancellableException(id, current);
        }
        Order order = orderRepository.findWithItemsById(id).orElseThrow(() -> orderNotFound(id));
        order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .forEach(item -> inventoryItemRepository.releaseStock(item.getProductId(), item.getQuantity()));
        log.info("Cancelled order {}", id);
        return OrderDetails.from(order);
    }

    private PlacementResult placeOrReplayWinner(String idempotencyKey, String requestHash, PlaceOrderCommand command) {
        try {
            return PlacementResult.created(orderPlacementService.placeNew(idempotencyKey, requestHash, command));
        } catch (DataIntegrityViolationException ex) {
            log.info("Order placement hit a constraint violation, checking for a concurrent duplicate");
            return findReplay(idempotencyKey, requestHash).orElseThrow(() -> ex);
        }
    }

    private Optional<PlacementResult> findReplay(String idempotencyKey, String requestHash) {
        return orderPlacementService.findByIdempotencyKey(idempotencyKey).map(stored -> {
            if (!stored.requestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            return PlacementResult.replayed(stored.details());
        });
    }

    private static void rejectDuplicateProducts(PlaceOrderCommand command) {
        Set<Long> seen = new HashSet<>();
        for (PlaceOrderCommand.Line line : command.lines()) {
            if (!seen.add(line.productId())) {
                throw new BadRequestException("Product %d appears more than once; combine its quantities into one item"
                        .formatted(line.productId()));
            }
        }
    }

    private static String fingerprint(PlaceOrderCommand command) {
        String items = command.lines().stream()
                .sorted(Comparator.comparing(PlaceOrderCommand.Line::productId))
                .map(line -> line.productId() + "x" + line.quantity())
                .collect(Collectors.joining(","));
        String canonical = "customer=" + command.customerId() + ";items=" + items;
        return HexFormat.of().formatHex(sha256(canonical));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static NotFoundException orderNotFound(Long id) {
        return new NotFoundException("Order %d not found".formatted(id));
    }
}
