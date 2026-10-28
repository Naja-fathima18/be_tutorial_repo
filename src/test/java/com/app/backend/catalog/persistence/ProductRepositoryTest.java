package com.app.backend.catalog.persistence;

import static com.app.backend.catalog.persistence.ProductSpecifications.hasCategory;
import static com.app.backend.catalog.persistence.ProductSpecifications.inStock;
import static com.app.backend.catalog.persistence.ProductSpecifications.nameContainsIgnoreCase;
import static com.app.backend.catalog.persistence.ProductSpecifications.priceAtLeast;
import static com.app.backend.catalog.persistence.ProductSpecifications.priceAtMost;
import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.catalog.domain.Product;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest
class ProductRepositoryTest {

    private static final String CATEGORY = "garden";
    private static final String OTHER_CATEGORY = "kitchen";

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void insertFixtures() {
        productRepository.saveAllAndFlush(List.of(
                new Product("Green Hose", CATEGORY, new BigDecimal("15.00"), 4, new BigDecimal("4.0")),
                new Product("Hose Reel", CATEGORY, new BigDecimal("45.00"), 0, new BigDecimal("3.5")),
                new Product("Rake", CATEGORY, new BigDecimal("25.00"), 10, new BigDecimal("2.0")),
                new Product("100%_Hose", CATEGORY, new BigDecimal("30.00"), 1, new BigDecimal("1.0")),
                new Product("Kitchen Hose", OTHER_CATEGORY, new BigDecimal("20.00"), 3, new BigDecimal("5.0"))));
    }

    private List<String> namesMatching(Specification<Product> specification) {
        return productRepository.findAll(specification, Sort.by("name")).stream()
                .map(Product::getName)
                .toList();
    }

    @Test
    void seedsOneHundredProductsOnStartup() {
        long seeded = productRepository.count(nameContainsIgnoreCase("product "));

        assertThat(seeded).isEqualTo(100);
    }

    @Test
    void seededProductsRespectConstraints() {
        List<Product> seeded = productRepository.findAll(nameContainsIgnoreCase("product "));

        assertThat(seeded).allSatisfy(product -> {
            assertThat(product.getPrice()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(product.getStock()).isNotNegative();
            assertThat(product.getRating()).isBetween(BigDecimal.ZERO, new BigDecimal("5.0"));
            assertThat(product.getCreatedAt()).isNotNull();
            assertThat(product.getVersion()).isZero();
        });
        assertThat(seeded).anyMatch(product -> product.getStock() == 0);
    }

    @Test
    void filtersByCategoryAndPriceRange() {
        List<String> names = namesMatching(Specification.allOf(
                hasCategory(CATEGORY), priceAtLeast(new BigDecimal("20")), priceAtMost(new BigDecimal("45.00"))));

        assertThat(names).containsExactly("100%_Hose", "Hose Reel", "Rake");
    }

    @Test
    void filtersByCategoryInStockAndNameIgnoringCase() {
        List<String> names = namesMatching(
                Specification.allOf(hasCategory(CATEGORY), inStock(true), nameContainsIgnoreCase("HOSE")));

        assertThat(names).containsExactly("100%_Hose", "Green Hose");
    }

    @Test
    void filtersOutOfStockProducts() {
        List<String> names = namesMatching(Specification.allOf(hasCategory(CATEGORY), inStock(false)));

        assertThat(names).containsExactly("Hose Reel");
    }

    @Test
    void combinesAllFilters() {
        List<String> names = namesMatching(Specification.allOf(
                hasCategory(CATEGORY),
                priceAtLeast(new BigDecimal("10")),
                priceAtMost(new BigDecimal("20")),
                inStock(true),
                nameContainsIgnoreCase("hose")));

        assertThat(names).containsExactly("Green Hose");
    }

    @Test
    void treatsLikeWildcardsInNameLiterally() {
        List<String> percent = namesMatching(nameContainsIgnoreCase("%"));
        List<String> underscore = namesMatching(nameContainsIgnoreCase("e_r"));

        assertThat(percent).containsExactly("100%_Hose");
        assertThat(underscore).isEmpty();
    }

    @Test
    void pagesAndSortsFilteredResults() {
        Page<Product> page = productRepository.findAll(
                hasCategory(CATEGORY), PageRequest.of(0, 2, Sort.by(Sort.Order.desc("price"))));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Hose Reel", "100%_Hose");
    }
}
