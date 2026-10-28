package com.app.backend.order.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.order.service.InventoryService;
import com.app.backend.order.service.StockLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void returnsCurrentStock() throws Exception {
        given(inventoryService.getStock(3L))
                .willReturn(new StockLevel(3L, "MN-001", "Monitor", new BigDecimal("249.00"), 8));

        mockMvc.perform(get("/api/inventory/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(3))
                .andExpect(jsonPath("$.sku").value("MN-001"))
                .andExpect(jsonPath("$.stock").value(8));
    }

    @Test
    void returnsNotFoundForUnknownProduct() throws Exception {
        given(inventoryService.getStock(99L)).willThrow(new NotFoundException("Product 99 not found"));

        mockMvc.perform(get("/api/inventory/99")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonPositiveProductId() throws Exception {
        mockMvc.perform(get("/api/inventory/0")).andExpect(status().isBadRequest());
    }
}
