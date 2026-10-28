package com.app.backend.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.catalog.service.ProductCommand;
import com.app.backend.catalog.service.ProductSearchCriteria;
import com.app.backend.catalog.service.ProductService;
import com.app.backend.catalog.service.ProductView;
import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    private static final String VALID_BODY = """
            {"name":"Desk Lamp","category":"home","price":19.99,"stock":5,"rating":4.5}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static ProductView view(long id) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new ProductView(
                id, "Desk Lamp", "home", new BigDecimal("19.99"), 5, new BigDecimal("4.5"), now, now, 0L);
    }

    @Test
    void createsProductAndReturnsLocation() throws Exception {
        given(productService.create(any(ProductCommand.class))).willReturn(view(101L));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/products/101")))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.price").value(19.99));

        then(productService)
                .should()
                .create(new ProductCommand("Desk Lamp", "home", new BigDecimal("19.99"), 5, new BigDecimal("4.5")));
    }

    @Test
    void rejectsInvalidProductWithFieldErrors() throws Exception {
        String body = """
                {"name":"","category":"home","price":-1,"stock":-2,"rating":5.5}
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(4)));

        then(productService).should(never()).create(any());
    }

    @Test
    void returnsProductById() throws Exception {
        given(productService.get(7L)).willReturn(view(7L));

        mockMvc.perform(get("/api/products/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Desk Lamp"));
    }

    @Test
    void returnsNotFoundForUnknownProduct() throws Exception {
        given(productService.get(999L)).willThrow(new NotFoundException("Product 999 not found"));

        mockMvc.perform(get("/api/products/999")).andExpect(status().isNotFound());
    }

    @Test
    void updatesProduct() throws Exception {
        given(productService.update(eq(7L), any(ProductCommand.class))).willReturn(view(7L));

        mockMvc.perform(put("/api/products/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void deletesProductWithNoContent() throws Exception {
        mockMvc.perform(delete("/api/products/7")).andExpect(status().isNoContent());

        then(productService).should().delete(7L);
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownProduct() throws Exception {
        willThrow(new NotFoundException("Product 999 not found"))
                .given(productService)
                .delete(999L);

        mockMvc.perform(delete("/api/products/999")).andExpect(status().isNotFound());
    }

    @Test
    void passesAllFiltersPagingAndSortingToService() throws Exception {
        given(productService.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(view(1L)), PageRequest.of(1, 5), 11));

        mockMvc.perform(get("/api/products")
                        .param("category", "home")
                        .param("minPrice", "10")
                        .param("maxPrice", "50.5")
                        .param("inStock", "true")
                        .param("name", "lamp")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<ProductSearchCriteria> criteria = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(productService).should().search(criteria.capture(), pageable.capture());
        assertThat(criteria.getValue())
                .isEqualTo(
                        new ProductSearchCriteria("home", new BigDecimal("10"), new BigDecimal("50.5"), true, "lamp"));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    @Test
    void searchesWithoutFiltersUsingDefaultPage() throws Exception {
        given(productService.search(any(), any())).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        ArgumentCaptor<ProductSearchCriteria> criteria = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(productService).should().search(criteria.capture(), pageable.capture());
        assertThat(criteria.getValue()).isEqualTo(new ProductSearchCriteria(null, null, null, null, null));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void rejectsNegativeMinPrice() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("minPrice"));

        then(productService).should(never()).search(any(), any());
    }

    @Test
    void rejectsNonNumericPrice() throws Exception {
        mockMvc.perform(get("/api/products").param("maxPrice", "cheap")).andExpect(status().isBadRequest());

        then(productService).should(never()).search(any(), any());
    }

    @Test
    void mapsServiceBadRequestToBadRequest() throws Exception {
        given(productService.search(any(), any()))
                .willThrow(new BadRequestException("minPrice must not be greater than maxPrice"));

        mockMvc.perform(get("/api/products").param("minPrice", "50").param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("minPrice must not be greater than maxPrice"));
    }
}
