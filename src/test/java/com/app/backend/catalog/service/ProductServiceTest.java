package com.app.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.app.backend.catalog.domain.Product;
import com.app.backend.catalog.persistence.ProductRepository;
import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final ProductSearchCriteria NO_FILTERS = new ProductSearchCriteria(null, null, null, null, null);

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private static Product product() {
        return new Product("Desk Lamp", "home", new BigDecimal("19.99"), 5, new BigDecimal("4.5"));
    }

    private static ProductCommand command() {
        return new ProductCommand("Floor Lamp", "home", new BigDecimal("49.00"), 0, new BigDecimal("3.0"));
    }

    @SuppressWarnings("unchecked")
    private Pageable searchAndCapturePageable(Pageable requested) {
        given(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        productService.search(NO_FILTERS, requested);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(productRepository).should().findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void createsProduct() {
        given(productRepository.saveAndFlush(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProductView created = productService.create(command());

        assertThat(created.name()).isEqualTo("Floor Lamp");
        assertThat(created.price()).isEqualByComparingTo("49.00");
        assertThat(created.stock()).isZero();
    }

    @Test
    void returnsExistingProduct() {
        given(productRepository.findById(1L)).willReturn(Optional.of(product()));

        ProductView view = productService.get(1L);

        assertThat(view.name()).isEqualTo("Desk Lamp");
    }

    @Test
    void throwsNotFoundForUnknownProduct() {
        given(productRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("9");
    }

    @Test
    void updatesExistingProduct() {
        Product existing = product();
        given(productRepository.findById(1L)).willReturn(Optional.of(existing));
        given(productRepository.saveAndFlush(existing)).willReturn(existing);

        ProductView updated = productService.update(1L, command());

        assertThat(updated.name()).isEqualTo("Floor Lamp");
        assertThat(updated.price()).isEqualByComparingTo("49.00");
        assertThat(updated.stock()).isZero();
        assertThat(updated.rating()).isEqualByComparingTo("3.0");
    }

    @Test
    void throwsNotFoundWhenUpdatingUnknownProduct() {
        given(productRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(9L, command())).isInstanceOf(NotFoundException.class);
        then(productRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void deletesExistingProduct() {
        Product existing = product();
        given(productRepository.findById(1L)).willReturn(Optional.of(existing));

        productService.delete(1L);

        then(productRepository).should().delete(existing);
    }

    @Test
    void throwsNotFoundWhenDeletingUnknownProduct() {
        given(productRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(9L)).isInstanceOf(NotFoundException.class);
        then(productRepository).should(never()).delete(any(Product.class));
    }

    @Test
    void rejectsMinPriceGreaterThanMaxPrice() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria(null, new BigDecimal("50"), new BigDecimal("10"), null, null);

        assertThatThrownBy(() -> productService.search(criteria, PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("minPrice");
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsEqualMinAndMaxPrice() {
        given(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        ProductSearchCriteria criteria =
                new ProductSearchCriteria(null, new BigDecimal("10.00"), new BigDecimal("10"), null, null);

        productService.search(criteria, PageRequest.of(0, 20));

        then(productRepository).should().findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsUnknownSortProperty() {
        assertThatThrownBy(() -> productService.search(NO_FILTERS, PageRequest.of(0, 20, Sort.by("password"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password");
    }

    @Test
    void clampsPageSizeToMaximum() {
        Pageable used = searchAndCapturePageable(PageRequest.of(2, 500));

        assertThat(used.getPageSize()).isEqualTo(ProductService.MAX_PAGE_SIZE);
        assertThat(used.getPageNumber()).isEqualTo(2);
    }

    @Test
    void keepsPageSizeWithinMaximum() {
        Pageable used = searchAndCapturePageable(PageRequest.of(0, 100));

        assertThat(used.getPageSize()).isEqualTo(100);
    }

    @Test
    void appendsIdAsTieBreakerToRequestedSort() {
        Pageable used = searchAndCapturePageable(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("price"))));

        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id")));
    }

    @Test
    void keepsExplicitIdSort() {
        Pageable used = searchAndCapturePageable(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("id"))));

        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Order.desc("id")));
    }
}
