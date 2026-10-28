package com.app.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import com.app.backend.catalog.persistence.ProductRepository;
import com.app.backend.catalog.service.ProductCommand;
import com.app.backend.catalog.service.ProductService;
import com.app.backend.catalog.service.ProductView;
import com.app.backend.common.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ProductCacheConsistencyIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Set<Thread.State> PARKED_STATES =
            EnumSet.of(Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING);

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(cacheManager.getCache(ProductService.PRODUCTS_CACHE))
                .clear();
    }

    private long createProduct(String name) {
        ProductCommand command = new ProductCommand(name, "it-race", new BigDecimal("10.00"), 1, new BigDecimal("3.0"));
        return productService.create(command).id();
    }

    private Long countRows(long id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Long.class, id);
    }

    private String storedName(long id) {
        return jdbcTemplate.queryForObject("SELECT name FROM products WHERE id = ?", String.class, id);
    }

    private void raceWriteAgainstInFlightLookup(long id, Runnable write, Callable<Boolean> writeCommitted)
            throws Exception {
        CountDownLatch staleRowRead = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        AtomicBoolean pauseNextRead = new AtomicBoolean(true);
        Answer<?> realRepositoryAnswer =
                mockingDetails(productRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
                    Object row = realRepositoryAnswer.answer(invocation);
                    if (pauseNextRead.compareAndSet(true, false)) {
                        staleRowRead.countDown();
                        releaseLookup.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                    }
                    return row;
                })
                .when(productRepository)
                .findById(id);

        FutureTask<ProductView> lookup = new FutureTask<>(() -> productService.get(id));
        new Thread(lookup, "catalog-lookup").start();
        assertThat(staleRowRead.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        FutureTask<Void> writer = new FutureTask<>(write, null);
        Thread writerThread = new Thread(writer, "catalog-writer");
        writerThread.start();
        await().atMost(TIMEOUT)
                .until(() ->
                        writeCommitted.call() && (writer.isDone() || PARKED_STATES.contains(writerThread.getState())));

        releaseLookup.countDown();
        lookup.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        writer.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    @Test
    void deleteDuringCacheMissNeverLeavesDeletedProductCached() throws Exception {
        long id = createProduct("Racing Delete");

        raceWriteAgainstInFlightLookup(id, () -> productService.delete(id), () -> countRows(id) == 0);

        assertThatThrownBy(() -> productService.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateDuringCacheMissNeverLeavesOldVersionCached() throws Exception {
        long id = createProduct("Racing Old");
        ProductCommand update =
                new ProductCommand("Racing New", "it-race", new BigDecimal("12.00"), 2, new BigDecimal("4.0"));

        raceWriteAgainstInFlightLookup(
                id, () -> productService.update(id, update), () -> "Racing New".equals(storedName(id)));

        ProductView afterRace = productService.get(id);
        assertThat(afterRace.name()).isEqualTo("Racing New");
        assertThat(afterRace.version()).isEqualTo(1L);
    }
}
