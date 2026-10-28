package com.app.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderConcurrencyIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sellsExactlyTheAvailableStockUnderFiftySimultaneousOrders() throws Exception {
        long productId = createProduct(10);

        List<ResponseEntity<String>> responses =
                runSimultaneously(50, attempt -> placeOrder(UUID.randomUUID().toString(), orderBody(productId, 1)));

        assertThat(countByStatus(responses, HttpStatus.CREATED)).isEqualTo(10);
        assertThat(countByStatus(responses, HttpStatus.CONFLICT)).isEqualTo(40);
        assertThat(stockOf(productId)).isZero();
        assertThat(ordersContaining(productId)).isEqualTo(10);
    }

    @Test
    void reservesStockOnceForConcurrentRetriesWithTheSameKey() throws Exception {
        long productId = createProduct(10);
        String key = UUID.randomUUID().toString();
        String body = orderBody(productId, 3);

        List<ResponseEntity<String>> responses = runSimultaneously(20, attempt -> placeOrder(key, body));

        assertThat(countByStatus(responses, HttpStatus.CREATED)).isEqualTo(1);
        assertThat(countByStatus(responses, HttpStatus.OK)).isEqualTo(19);
        assertThat(responses.stream().map(this::orderId).distinct()).hasSize(1);
        assertThat(stockOf(productId)).isEqualTo(7);
        assertThat(ordersWithKey(key)).isEqualTo(1);
    }

    @Test
    void replaysTheOriginalOrderForASequentialRetry() throws Exception {
        long productId = createProduct(5);
        String key = UUID.randomUUID().toString();
        String body = orderBody(productId, 2);

        ResponseEntity<String> first = placeOrder(key, body);
        ResponseEntity<String> retry = placeOrder(key, body);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().getLocation()).isNotNull();
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(retry.getBody())).isEqualTo(objectMapper.readTree(first.getBody()));
        assertThat(stockOf(productId)).isEqualTo(3);
    }

    @Test
    void rejectsReusedKeyWithDifferentPayload() {
        long productId = createProduct(5);
        String key = UUID.randomUUID().toString();
        placeOrder(key, orderBody(productId, 1));

        ResponseEntity<String> reused = placeOrder(key, orderBody(productId, 2));

        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(stockOf(productId)).isEqualTo(4);
    }

    @Test
    void reservesNothingWhenOneItemOfAMultiItemOrderLacksStock() throws Exception {
        long plentiful = createProduct(10);
        long scarce = createProduct(1);
        String body = """
                {"customerId":1,"items":[{"productId":%d,"quantity":4},{"productId":%d,"quantity":2}]}
                """.formatted(plentiful, scarce);

        ResponseEntity<String> response = placeOrder(UUID.randomUUID().toString(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.get("detail").asText())
                .contains("product " + scarce)
                .contains("requested 2")
                .contains("available 1");
        assertThat(stockOf(plentiful)).isEqualTo(10);
        assertThat(stockOf(scarce)).isEqualTo(1);
        assertThat(ordersContaining(plentiful)).isZero();
    }

    @Test
    void cancellingReturnsStockAndCancellingAgainConflicts() throws Exception {
        long productId = createProduct(5);
        ResponseEntity<String> placed = placeOrder(UUID.randomUUID().toString(), orderBody(productId, 3));
        long orderId = orderId(placed);

        ResponseEntity<String> cancelled = cancel(orderId);
        ResponseEntity<String> cancelledAgain = cancel(orderId);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(cancelled.getBody()).get("status").asText())
                .isEqualTo("CANCELLED");
        assertThat(cancelledAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(productId)).isEqualTo(5);
        assertThat(inventoryStock(productId)).isEqualTo(5);
    }

    @Test
    void returnsStockOnlyOnceForConcurrentCancellations() throws Exception {
        long productId = createProduct(5);
        long orderId = orderId(placeOrder(UUID.randomUUID().toString(), orderBody(productId, 4)));

        List<ResponseEntity<String>> responses = runSimultaneously(10, attempt -> cancel(orderId));

        assertThat(countByStatus(responses, HttpStatus.OK)).isEqualTo(1);
        assertThat(countByStatus(responses, HttpStatus.CONFLICT)).isEqualTo(9);
        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    void returnsTheStoredOrderById() throws Exception {
        long productId = createProduct(5);
        ResponseEntity<String> placed = placeOrder(UUID.randomUUID().toString(), orderBody(productId, 2));

        ResponseEntity<String> fetched =
                restTemplate.getForEntity(placed.getHeaders().getLocation(), String.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = objectMapper.readTree(fetched.getBody());
        assertThat(order.get("status").asText()).isEqualTo("PLACED");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(order.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
    }

    private List<ResponseEntity<String>> runSimultaneously(int count, IntFunction<ResponseEntity<String>> request)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < count; attempt++) {
                int current = attempt;
                Callable<ResponseEntity<String>> task = () -> {
                    startGate.await();
                    return request.apply(current);
                };
                futures.add(executor.submit(task));
            }
            startGate.countDown();
            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private ResponseEntity<String> placeOrder(String idempotencyKey, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.postForEntity("/api/orders", new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> cancel(long orderId) {
        return restTemplate.postForEntity("/api/orders/{id}/cancel", null, String.class, orderId);
    }

    private int inventoryStock(long productId) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/inventory/{id}", String.class, productId);
        return objectMapper.readTree(response.getBody()).get("stock").asInt();
    }

    private static String orderBody(long productId, int quantity) {
        return """
                {"customerId":1,"items":[{"productId":%d,"quantity":%d}]}
                """.formatted(productId, quantity);
    }

    private long orderId(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody()).get("id").asLong();
        } catch (Exception ex) {
            throw new IllegalStateException("Response is not an order: " + response, ex);
        }
    }

    private static long countByStatus(List<ResponseEntity<String>> responses, HttpStatus status) {
        return responses.stream()
                .filter(response -> response.getStatusCode().equals(status))
                .count();
    }

    private long createProduct(int stock) {
        String sku = "IT-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO inventory_items (sku, name, price, stock) VALUES (?, ?, ?, ?)",
                sku,
                "Test item " + sku,
                10,
                stock);
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT id FROM inventory_items WHERE sku = ?", sku);
        return ((Number) row.get("id")).longValue();
    }

    private int stockOf(long productId) {
        Integer stock =
                jdbcTemplate.queryForObject("SELECT stock FROM inventory_items WHERE id = ?", Integer.class, productId);
        return stock == null ? -1 : stock;
    }

    private int ordersContaining(long productId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT order_id) FROM order_items WHERE product_id = ?", Integer.class, productId);
        return count == null ? 0 : count;
    }

    private int ordersWithKey(String idempotencyKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE idempotency_key = ?", Integer.class, idempotencyKey);
        return count == null ? 0 : count;
    }
}
