package com.app.backend.catalog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.catalog.persistence.ProductRepository;
import com.app.backend.catalog.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ProductCatalogIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @BeforeEach
    void resetCacheAndSpy() {
        Objects.requireNonNull(cacheManager.getCache(ProductService.PRODUCTS_CACHE))
                .clear();
        clearInvocations(productRepository);
    }

    private long createProduct(String name, String category, String price, int stock, String rating) throws Exception {
        String body = """
                {"name":"%s","category":"%s","price":%s,"stock":%d,"rating":%s}
                """.formatted(name, category, price, stock, rating);
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    @Test
    void repeatedLookupHitsDatabaseOnlyOnce() throws Exception {
        long id = createProduct("Cached Kettle", "it-cache", "30.00", 3, "4.0");
        clearInvocations(productRepository);

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(get("/api/products/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Cached Kettle"));
        }

        verify(productRepository, times(1)).findById(id);
    }

    @Test
    void lookupAfterUpdateReloadsFreshDataOnceAndCachesIt() throws Exception {
        long id = createProduct("Old Kettle", "it-update", "30.00", 3, "4.0");
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(jsonPath("$.name").value("Old Kettle"));
        String update = """
                {"name":"New Kettle","category":"it-update","price":35.50,"stock":7,"rating":4.5}
                """;

        mockMvc.perform(put("/api/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        clearInvocations(productRepository);

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Kettle"))
                .andExpect(jsonPath("$.price").value(35.5))
                .andExpect(jsonPath("$.stock").value(7));
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(jsonPath("$.name").value("New Kettle"));
        verify(productRepository, times(1)).findById(id);
    }

    @Test
    void lookupAfterDeleteReturnsNotFound() throws Exception {
        long id = createProduct("Doomed Kettle", "it-delete", "30.00", 3, "4.0");
        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/products/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void updatingUnknownProductReturnsNotFound() throws Exception {
        String update = """
                {"name":"Ghost","category":"none","price":1,"stock":1,"rating":1}
                """;

        mockMvc.perform(put("/api/products/{id}", 999_999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesCacheStatisticsThroughActuator() throws Exception {
        long id = createProduct("Metered Kettle", "it-metrics", "30.00", 3, "4.0");
        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/caches"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.cacheManagers.cacheManager.caches.products").exists());
        mockMvc.perform(get("/actuator/metrics/cache.gets")
                        .param("tag", "cache:products")
                        .param("tag", "result:hit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value", greaterThanOrEqualTo(1.0)));
        mockMvc.perform(get("/actuator/metrics/cache.gets")
                        .param("tag", "cache:products")
                        .param("tag", "result:miss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value", greaterThanOrEqualTo(1.0)));
    }

    @Test
    void listsSeededCatalogWithPagingMetadata() throws Exception {
        mockMvc.perform(get("/api/products").param("name", "product ").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(100))
                .andExpect(jsonPath("$.totalPages").value(10));
    }

    @Test
    void capsPageSizeAtOneHundred() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(100)));
    }

    @Test
    void combinesCategoryPriceStockAndNameFilters() throws Exception {
        createProduct("Blue Mug", "it-filter", "8.00", 2, "4.0");
        createProduct("Red Mug", "it-filter", "12.00", 0, "3.0");
        createProduct("Green Mug", "it-filter", "15.00", 5, "5.0");
        createProduct("Teapot", "it-filter", "14.00", 5, "2.0");

        mockMvc.perform(get("/api/products")
                        .param("category", "it-filter")
                        .param("minPrice", "10")
                        .param("maxPrice", "20")
                        .param("inStock", "true")
                        .param("name", "MUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].name", contains("Green Mug")));
        mockMvc.perform(get("/api/products").param("category", "it-filter").param("inStock", "false"))
                .andExpect(jsonPath("$.content[*].name", contains("Red Mug")));
        mockMvc.perform(get("/api/products")
                        .param("category", "it-filter")
                        .param("maxPrice", "14")
                        .param("sort", "price,desc"))
                .andExpect(jsonPath("$.content[*].name", contains("Teapot", "Red Mug", "Blue Mug")));
        mockMvc.perform(get("/api/products").param("name", "mug").param("sort", "rating,asc"))
                .andExpect(jsonPath("$.content[*].name", contains("Red Mug", "Blue Mug", "Green Mug")));
    }

    @Test
    void sortsBySeveralFields() throws Exception {
        createProduct("Alpha Fork", "it-sort", "5.00", 1, "1.0");
        createProduct("Beta Fork", "it-sort", "5.00", 9, "2.0");

        mockMvc.perform(get("/api/products")
                        .param("category", "it-sort")
                        .param("sort", "price,asc")
                        .param("sort", "stock,desc"))
                .andExpect(jsonPath("$.content[*].name", contains("Beta Fork", "Alpha Fork")));
        mockMvc.perform(get("/api/products").param("category", "it-sort").param("sort", "name,desc"))
                .andExpect(jsonPath("$.content[*].name", contains("Beta Fork", "Alpha Fork")));
        mockMvc.perform(get("/api/products").param("category", "it-sort").param("sort", "createdAt,asc"))
                .andExpect(jsonPath("$.content[*].name", contains("Alpha Fork", "Beta Fork")));
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "secret,asc")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvertedPriceRange() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "50").param("maxPrice", "10"))
                .andExpect(status().isBadRequest());
    }
}
