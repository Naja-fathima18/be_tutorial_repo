package com.app.backend.order.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.order.domain.IdempotencyKeyReusedException;
import com.app.backend.order.domain.InsufficientStockException;
import com.app.backend.order.domain.OrderNotCancellableException;
import com.app.backend.order.domain.OrderStatus;
import com.app.backend.order.service.OrderDetails;
import com.app.backend.order.service.OrderLineDetails;
import com.app.backend.order.service.OrderService;
import com.app.backend.order.service.PlaceOrderCommand;
import com.app.backend.order.service.PlacementResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final String VALID_BODY = """
            {"customerId":7,"items":[{"productId":3,"quantity":2}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static OrderDetails order(long id, OrderStatus status) {
        return new OrderDetails(
                id,
                7L,
                status,
                List.of(new OrderLineDetails(3L, "Monitor", new BigDecimal("249.00"), 2, new BigDecimal("498.00"))),
                new BigDecimal("498.00"),
                Instant.parse("2026-01-01T10:00:00Z"));
    }

    private static MockHttpServletRequestBuilder placeOrder(String key, String body) {
        MockHttpServletRequestBuilder request =
                post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body);
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    @Test
    void placesOrderAndReturnsCreatedWithLocation() throws Exception {
        PlaceOrderCommand expectedCommand = new PlaceOrderCommand(7L, List.of(new PlaceOrderCommand.Line(3L, 2)));
        given(orderService.placeOrder("key-1", expectedCommand))
                .willReturn(new PlacementResult(order(11, OrderStatus.PLACED), true));

        mockMvc.perform(placeOrder("key-1", VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/orders/11")))
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.total").value(498.00))
                .andExpect(jsonPath("$.items[0].productId").value(3))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"));
    }

    @Test
    void returnsOkWithOriginalOrderWhenRequestIsReplayed() throws Exception {
        given(orderService.placeOrder(eq("key-1"), any()))
                .willReturn(new PlacementResult(order(11, OrderStatus.PLACED), false));

        mockMvc.perform(placeOrder("key-1", VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Location", endsWith("/api/orders/11")))
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void rejectsOrderWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(placeOrder(null, VALID_BODY)).andExpect(status().isBadRequest());

        then(orderService).should(never()).placeOrder(anyString(), any());
    }

    @Test
    void rejectsBlankIdempotencyKey() throws Exception {
        mockMvc.perform(placeOrder(" ", VALID_BODY)).andExpect(status().isBadRequest());

        then(orderService).should(never()).placeOrder(anyString(), any());
    }

    @Test
    void rejectsIdempotencyKeyLongerThanOneHundredCharacters() throws Exception {
        mockMvc.perform(placeOrder("k".repeat(101), VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"));

        then(orderService).should(never()).placeOrder(anyString(), any());
    }

    @Test
    void acceptsIdempotencyKeyOfExactlyOneHundredCharacters() throws Exception {
        given(orderService.placeOrder(anyString(), any()))
                .willReturn(new PlacementResult(order(11, OrderStatus.PLACED), true));

        mockMvc.perform(placeOrder("k".repeat(100), VALID_BODY)).andExpect(status().isCreated());
    }

    @Test
    void rejectsOrderWithoutItems() throws Exception {
        mockMvc.perform(placeOrder("key-1", """
                        {"customerId":7,"items":[]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    void rejectsItemWithQuantityBelowOne() throws Exception {
        mockMvc.perform(placeOrder("key-1", """
                        {"customerId":7,"items":[{"productId":3,"quantity":0}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @Test
    void rejectsOrderWithoutCustomer() throws Exception {
        mockMvc.perform(placeOrder("key-1", """
                        {"items":[{"productId":3,"quantity":1}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customerId"));
    }

    @Test
    void mapsDuplicateProductsToBadRequest() throws Exception {
        given(orderService.placeOrder(anyString(), any()))
                .willThrow(new BadRequestException("Product 3 appears more than once"));

        mockMvc.perform(placeOrder("key-1", VALID_BODY)).andExpect(status().isBadRequest());
    }

    @Test
    void mapsInsufficientStockToConflictProblem() throws Exception {
        given(orderService.placeOrder(anyString(), any()))
                .willThrow(new InsufficientStockException(3L, "Monitor", 2, 1));

        mockMvc.perform(placeOrder("key-1", VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("requested 2, available 1")));
    }

    @Test
    void mapsUnknownProductToNotFound() throws Exception {
        given(orderService.placeOrder(anyString(), any())).willThrow(new NotFoundException("Product 3 not found"));

        mockMvc.perform(placeOrder("key-1", VALID_BODY)).andExpect(status().isNotFound());
    }

    @Test
    void mapsReusedKeyWithDifferentPayloadToUnprocessableEntity() throws Exception {
        given(orderService.placeOrder(anyString(), any())).willThrow(new IdempotencyKeyReusedException());

        mockMvc.perform(placeOrder("key-1", VALID_BODY)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returnsOrderById() throws Exception {
        given(orderService.getOrder(11L)).willReturn(order(11, OrderStatus.PLACED));

        mockMvc.perform(get("/api/orders/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.customerId").value(7));
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        given(orderService.getOrder(99L)).willThrow(new NotFoundException("Order 99 not found"));

        mockMvc.perform(get("/api/orders/99")).andExpect(status().isNotFound());
    }

    @Test
    void cancelsOrder() throws Exception {
        given(orderService.cancelOrder(11L)).willReturn(order(11, OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/orders/11/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void returnsConflictWhenCancellingAnAlreadyCancelledOrder() throws Exception {
        given(orderService.cancelOrder(11L)).willThrow(new OrderNotCancellableException(11L, OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/orders/11/cancel")).andExpect(status().isConflict());
    }
}
