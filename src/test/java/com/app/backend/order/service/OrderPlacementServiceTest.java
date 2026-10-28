package com.app.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.withSettings;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.order.domain.InsufficientStockException;
import com.app.backend.order.domain.InventoryItem;
import com.app.backend.order.domain.Order;
import com.app.backend.order.persistence.InventoryItemRepository;
import com.app.backend.order.persistence.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private OrderPlacementService orderPlacementService;

    private static InventoryItem product(long id, String price) {
        InventoryItem product = mock(InventoryItem.class, withSettings().strictness(Strictness.LENIENT));
        given(product.getId()).willReturn(id);
        given(product.getName()).willReturn("Product " + id);
        given(product.getPrice()).willReturn(new BigDecimal(price));
        return product;
    }

    private static PlaceOrderCommand command(PlaceOrderCommand.Line... lines) {
        return new PlaceOrderCommand(7L, List.of(lines));
    }

    @Test
    void claimsTheKeyThenReservesItemsInAscendingProductOrder() {
        List<InventoryItem> products = List.of(product(9L, "1.50"), product(2L, "10.00"));
        given(inventoryItemRepository.findAllById(List.of(2L, 9L))).willReturn(products);
        given(orderRepository.saveAndFlush(any(Order.class))).will(returnsFirstArg());
        given(inventoryItemRepository.reserveStock(anyLong(), anyInt())).willReturn(1);

        OrderDetails placed = orderPlacementService.placeNew(
                "key-1", "hash", command(new PlaceOrderCommand.Line(9L, 2), new PlaceOrderCommand.Line(2L, 3)));

        InOrder sequence = inOrder(orderRepository, inventoryItemRepository);
        sequence.verify(orderRepository).saveAndFlush(any(Order.class));
        sequence.verify(inventoryItemRepository).reserveStock(2L, 3);
        sequence.verify(inventoryItemRepository).reserveStock(9L, 2);
        assertThat(placed.total()).isEqualByComparingTo("33.00");
        assertThat(placed.items()).extracting(OrderLineDetails::productId).containsExactly(2L, 9L);
    }

    @Test
    void rejectsUnknownProductWithoutClaimingTheKeyOrReservingStock() {
        List<InventoryItem> products = List.of(product(2L, "10.00"));
        given(inventoryItemRepository.findAllById(List.of(2L, 9L))).willReturn(products);

        assertThatThrownBy(() -> orderPlacementService.placeNew(
                        "key-1", "hash", command(new PlaceOrderCommand.Line(2L, 1), new PlaceOrderCommand.Line(9L, 1))))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product 9 not found");
        then(orderRepository).should(never()).saveAndFlush(any());
        then(inventoryItemRepository).should(never()).reserveStock(anyLong(), anyInt());
    }

    @Test
    void failsWithRequestedAndAvailableQuantityWhenStockIsInsufficient() {
        List<InventoryItem> products = List.of(product(2L, "10.00"), product(9L, "1.50"));
        given(inventoryItemRepository.findAllById(List.of(2L, 9L))).willReturn(products);
        given(orderRepository.saveAndFlush(any(Order.class))).will(returnsFirstArg());
        given(inventoryItemRepository.reserveStock(2L, 1)).willReturn(1);
        given(inventoryItemRepository.reserveStock(9L, 5)).willReturn(0);
        given(inventoryItemRepository.findStockById(9L)).willReturn(Optional.of(3));

        assertThatThrownBy(() -> orderPlacementService.placeNew(
                        "key-1", "hash", command(new PlaceOrderCommand.Line(2L, 1), new PlaceOrderCommand.Line(9L, 5))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for product 9 (Product 9): requested 5, available 3");
    }

    @Test
    void findsStoredOrderWithItsRequestHash() {
        given(orderRepository.findWithItemsByIdempotencyKey("key-1"))
                .willReturn(Optional.of(new Order(7L, "key-1", "hash")));

        Optional<StoredOrder> stored = orderPlacementService.findByIdempotencyKey("key-1");

        assertThat(stored).hasValueSatisfying(order -> {
            assertThat(order.requestHash()).isEqualTo("hash");
            assertThat(order.details().customerId()).isEqualTo(7L);
        });
    }
}
