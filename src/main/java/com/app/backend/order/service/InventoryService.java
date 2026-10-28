package com.app.backend.order.service;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.order.persistence.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Transactional(readOnly = true)
    public StockLevel getStock(Long productId) {
        return inventoryItemRepository
                .findById(productId)
                .map(StockLevel::from)
                .orElseThrow(() -> new NotFoundException("Product %d not found".formatted(productId)));
    }
}
