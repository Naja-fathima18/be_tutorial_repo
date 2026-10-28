package com.app.backend.catalog.service;

import com.app.backend.catalog.domain.Product;
import com.app.backend.catalog.persistence.ProductRepository;
import com.app.backend.catalog.persistence.ProductSpecifications;
import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductService {

    public static final String PRODUCTS_CACHE = "products";
    static final int MAX_PAGE_SIZE = 100;
    static final Set<String> SORTABLE_PROPERTIES =
            Set.of("id", "name", "category", "price", "stock", "rating", "createdAt", "updatedAt", "version");

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductView create(ProductCommand command) {
        Product product =
                new Product(command.name(), command.category(), command.price(), command.stock(), command.rating());
        Product saved = productRepository.saveAndFlush(product);
        log.info("Created product {}", saved.getId());
        return ProductView.from(saved);
    }

    @Cacheable(cacheNames = PRODUCTS_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public ProductView get(long id) {
        return ProductView.from(findProduct(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductView> search(ProductSearchCriteria criteria, Pageable pageable) {
        requireValidPriceRange(criteria);
        return productRepository
                .findAll(specificationFor(criteria), bounded(pageable))
                .map(ProductView::from);
    }

    @CachePut(cacheNames = PRODUCTS_CACHE, key = "#id")
    @Transactional
    public ProductView update(long id, ProductCommand command) {
        Product product = findProduct(id);
        product.update(command.name(), command.category(), command.price(), command.stock(), command.rating());
        Product saved = productRepository.saveAndFlush(product);
        log.info("Updated product {}", id);
        return ProductView.from(saved);
    }

    @CacheEvict(cacheNames = PRODUCTS_CACHE, key = "#id")
    @Transactional
    public void delete(long id) {
        Product product = findProduct(id);
        productRepository.delete(product);
        productRepository.flush();
        log.info("Deleted product {}", id);
    }

    private Product findProduct(long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    private static void requireValidPriceRange(ProductSearchCriteria criteria) {
        if (criteria.minPrice() != null
                && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }
    }

    private static Specification<Product> specificationFor(ProductSearchCriteria criteria) {
        List<Specification<Product>> specifications = new ArrayList<>();
        if (StringUtils.hasText(criteria.category())) {
            specifications.add(
                    ProductSpecifications.hasCategory(criteria.category().strip()));
        }
        if (criteria.minPrice() != null) {
            specifications.add(ProductSpecifications.priceAtLeast(criteria.minPrice()));
        }
        if (criteria.maxPrice() != null) {
            specifications.add(ProductSpecifications.priceAtMost(criteria.maxPrice()));
        }
        if (criteria.inStock() != null) {
            specifications.add(ProductSpecifications.inStock(criteria.inStock()));
        }
        if (StringUtils.hasText(criteria.name())) {
            specifications.add(
                    ProductSpecifications.nameContainsIgnoreCase(criteria.name().strip()));
        }
        return Specification.allOf(specifications);
    }

    private static Pageable bounded(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException("Unsupported sort property: " + order.getProperty());
            }
        }
        Sort sort = pageable.getSort().getOrderFor("id") == null
                ? pageable.getSort().and(Sort.by("id"))
                : pageable.getSort();
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
