package com.app.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.withSettings;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.order.domain.IdempotencyKeyReusedException;
import com.app.backend.order.domain.InventoryItem;
import com.app.backend.order.domain.Order;
import com.app.backend.order.domain.OrderNotCancellableException;
import com.app.backend.order.domain.OrderStatus;
import com.app.backend.order.persistence.InventoryItemRepository;
import com.app.backend.order.persistence.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String KEY = "key-1";

    @Mock
    private OrderPlacementService orderPlacementService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private OrderService orderService;

    private static PlaceOrderCommand command(PlaceOrderCommand.Line... lines) {
        return new PlaceOrderCommand(7L, List.of(lines));
    }

    private static PlaceOrderCommand.Line line(long productId, int quantity) {
        return new PlaceOrderCommand.Line(productId, quantity);
    }

    private static OrderDetails details(long id) {
        return new OrderDetails(id, 7L, OrderStatus.PLACED, List.of(), BigDecimal.TEN, Instant.now());
    }

    private String hashPlacedFor(PlaceOrderCommand command) {
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        given(orderPlacementService.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(orderPlacementService.placeNew(eq(KEY), hash.capture(), any())).willReturn(details(1));
        orderService.placeOrder(KEY, command);
        return hash.getValue();
    }

    @Test
    void placesNewOrderWhenKeyIsUnused() {
        PlaceOrderCommand command = command(line(1, 2));
        given(orderPlacementService.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(orderPlacementService.placeNew(eq(KEY), anyString(), eq(command))).willReturn(details(5));

        PlacementResult result = orderService.placeOrder(KEY, command);

        assertThat(result.created()).isTrue();
        assertThat(result.order().id()).isEqualTo(5);
    }

    @Test
    void replaysStoredOrderWithoutPlacingAgainWhenPayloadMatches() {
        PlaceOrderCommand command = command(line(1, 2));
        String hash = hashPlacedFor(command);
        given(orderPlacementService.findByIdempotencyKey(KEY))
                .willReturn(Optional.of(new StoredOrder(hash, details(5))));

        PlacementResult result = orderService.placeOrder(KEY, command);

        assertThat(result.created()).isFalse();
        assertThat(result.order().id()).isEqualTo(5);
        then(orderPlacementService).should().placeNew(anyString(), anyString(), any());
    }

    @Test
    void rejectsReusedKeyWhenPayloadDiffers() {
        given(orderPlacementService.findByIdempotencyKey(KEY))
                .willReturn(Optional.of(new StoredOrder("other-hash", details(5))));

        assertThatThrownBy(() -> orderService.placeOrder(KEY, command(line(1, 2))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        then(orderPlacementService).should(never()).placeNew(anyString(), anyString(), any());
    }

    @Test
    void fingerprintIgnoresItemOrderButNotQuantities() {
        String forward = hashPlacedFor(command(line(1, 2), line(2, 1)));
        String reversed = hashPlacedFor(command(line(2, 1), line(1, 2)));
        String changedQuantity = hashPlacedFor(command(line(1, 3), line(2, 1)));

        assertThat(forward).isEqualTo(reversed).hasSize(64);
        assertThat(changedQuantity).isNotEqualTo(forward);
    }

    @Test
    void fingerprintDependsOnCustomer() {
        String customerSeven = hashPlacedFor(command(line(1, 2)));
        String customerEight = hashPlacedFor(new PlaceOrderCommand(8L, List.of(line(1, 2))));

        assertThat(customerEight).isNotEqualTo(customerSeven);
    }

    @Test
    void returnsConcurrentWinnerWhenUniqueKeyIsViolated() {
        PlaceOrderCommand command = command(line(1, 2));
        String hash = hashPlacedFor(command);
        given(orderPlacementService.findByIdempotencyKey(KEY))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(new StoredOrder(hash, details(9))));
        given(orderPlacementService.placeNew(eq(KEY), eq(hash), eq(command)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        PlacementResult result = orderService.placeOrder(KEY, command);

        assertThat(result.created()).isFalse();
        assertThat(result.order().id()).isEqualTo(9);
    }

    @Test
    void rejectsConcurrentWinnerWithDifferentPayload() {
        given(orderPlacementService.findByIdempotencyKey(KEY))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(new StoredOrder("other-hash", details(9))));
        given(orderPlacementService.placeNew(eq(KEY), anyString(), any()))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> orderService.placeOrder(KEY, command(line(1, 2))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void rethrowsIntegrityViolationWhenNoConcurrentWinnerExists() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("check constraint");
        given(orderPlacementService.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(orderPlacementService.placeNew(eq(KEY), anyString(), any())).willThrow(violation);

        assertThatThrownBy(() -> orderService.placeOrder(KEY, command(line(1, 2))))
                .isSameAs(violation);
    }

    @Test
    void rejectsDuplicateProductsBeforeTouchingStorage() {
        assertThatThrownBy(() -> orderService.placeOrder(KEY, command(line(1, 2), line(1, 3))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Product 1");
        then(orderPlacementService).shouldHaveNoInteractions();
    }

    @Test
    void returnsOrderById() {
        Order order = new Order(7L, KEY, "hash");
        given(orderRepository.findWithItemsById(3L)).willReturn(Optional.of(order));

        OrderDetails details = orderService.getOrder(3L);

        assertThat(details.customerId()).isEqualTo(7L);
        assertThat(details.status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void throwsNotFoundForUnknownOrder() {
        given(orderRepository.findWithItemsById(3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(3L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelReturnsStockForEveryItemInAscendingProductOrder() {
        Order order = new Order(7L, KEY, "hash");
        order.addItem(product(5L), 2);
        order.addItem(product(2L), 4);
        given(orderRepository.transitionStatus(3L, OrderStatus.PLACED, OrderStatus.CANCELLED))
                .willReturn(1);
        given(orderRepository.findWithItemsById(3L)).willReturn(Optional.of(order));

        orderService.cancelOrder(3L);

        InOrder releases = inOrder(inventoryItemRepository);
        releases.verify(inventoryItemRepository).releaseStock(2L, 4);
        releases.verify(inventoryItemRepository).releaseStock(5L, 2);
        releases.verifyNoMoreInteractions();
    }

    @Test
    void cancelConflictsWithoutReturningStockWhenOrderIsNotPlaced() {
        given(orderRepository.transitionStatus(3L, OrderStatus.PLACED, OrderStatus.CANCELLED))
                .willReturn(0);
        given(orderRepository.findStatusById(3L)).willReturn(Optional.of(OrderStatus.CANCELLED));

        assertThatThrownBy(() -> orderService.cancelOrder(3L))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessageContaining("CANCELLED");
        then(inventoryItemRepository).should(never()).releaseStock(anyLong(), anyInt());
    }

    @Test
    void cancelThrowsNotFoundForUnknownOrder() {
        given(orderRepository.transitionStatus(3L, OrderStatus.PLACED, OrderStatus.CANCELLED))
                .willReturn(0);
        given(orderRepository.findStatusById(3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(3L)).isInstanceOf(NotFoundException.class);
        then(inventoryItemRepository).should(never()).releaseStock(anyLong(), anyInt());
    }

    private static InventoryItem product(long id) {
        InventoryItem product = mock(InventoryItem.class, withSettings().strictness(Strictness.LENIENT));
        given(product.getId()).willReturn(id);
        given(product.getName()).willReturn("Product " + id);
        given(product.getPrice()).willReturn(BigDecimal.ONE);
        return product;
    }
}
