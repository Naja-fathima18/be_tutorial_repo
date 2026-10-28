package com.app.backend.order.api;

import com.app.backend.order.service.OrderService;
import com.app.backend.order.service.PlacementResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        PlacementResult result = orderService.placeOrder(idempotencyKey, request.toCommand());
        OrderResponse body = OrderResponse.from(result.order());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/orders/{id}")
                .buildAndExpand(body.id())
                .toUri();
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).location(location).body(body);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable @Positive Long id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable @Positive Long id) {
        return OrderResponse.from(orderService.cancelOrder(id));
    }
}
