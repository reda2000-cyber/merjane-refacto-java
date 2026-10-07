package com.nimbleways.springboilerplate.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.utils.Annotations.UnitTest;

@ExtendWith(MockitoExtension.class)
@UnitTest
class ProductServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
        productService = new ProductService(productRepository, notificationService, clock);
    }

    @Test
    void decrementsAvailableNormalProduct() {
        Product product = product("NORMAL", 15, 2, null, null, null);

        productService.processProduct(product);

        assertEquals(1, product.getAvailable());
        verify(productRepository).save(product);
        verifyNoInteractions(notificationService);
    }

    @Test
    void notifiesDelayForNormalProductWithoutStock() {
        Product product = product("NORMAL", 15, 0, null, null, null);

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        verify(notificationService).sendDelayNotification(15, product.getName());
        verify(productRepository).save(product);
    }

    @Test
    void notifyDelayPersistsLeadTimeAndNotifies() {
        Product product = product("NORMAL", 5, 0, null, null, null);

        productService.notifyDelay(15, product);

        assertEquals(15, product.getLeadTime());
        verify(productRepository).save(product);
        verify(notificationService).sendDelayNotification(15, product.getName());
    }

    @Test
    void doesNotNotifyWhenNormalProductHasNoPositiveLeadTime() {
        Product product = product("NORMAL", 0, 0, null, null, null);

        productService.processProduct(product);

        verifyNoInteractions(notificationService, productRepository);
    }

    @Test
    void sellsSeasonalProductOnSeasonStartDate() {
        Product product = product("SEASONAL", 2, 1, null, TODAY, TODAY.plusDays(4));

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        verify(productRepository).save(product);
        verifyNoInteractions(notificationService);
    }

    @Test
    void sellsSeasonalProductOnSeasonEndDate() {
        Product product = product("SEASONAL", 2, 1, null, TODAY.minusDays(4), TODAY);

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        verify(productRepository).save(product);
        verifyNoInteractions(notificationService);
    }

    @Test
    void notifiesDelayWhenSeasonalRestockFitsWithinSeason() {
        Product product = product("SEASONAL", 2, 0, null, TODAY.minusDays(2), TODAY.plusDays(4));

        productService.processProduct(product);

        verify(notificationService).sendDelayNotification(2, product.getName());
        verify(productRepository).save(product);
    }

    @Test
    void notifiesUnavailableWhenSeasonalRestockMissesSeason() {
        Product product = product("SEASONAL", 5, 0, null, TODAY.minusDays(2), TODAY.plusDays(4));

        productService.processProduct(product);

        verify(notificationService).sendOutOfStockNotification(product.getName());
        verify(notificationService, never()).sendDelayNotification(5, product.getName());
        verifyNoInteractions(productRepository);
    }

    @Test
    void notifiesUnavailableBeforeSeasonStarts() {
        Product product = product("SEASONAL", 2, 3, null, TODAY.plusDays(1), TODAY.plusDays(5));

        productService.processProduct(product);

        verify(notificationService).sendOutOfStockNotification(product.getName());
        verifyNoInteractions(productRepository);
    }

    @Test
    void sellsExpirableProductOnExpiryDate() {
        Product product = product("EXPIRABLE", 2, 1, TODAY, null, null);

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        verify(productRepository).save(product);
        verifyNoInteractions(notificationService);
    }

    @Test
    void marksExpiredProductUnavailableAndNotifies() {
        LocalDate expiryDate = TODAY.minusDays(1);
        Product product = product("EXPIRABLE", 2, 3, expiryDate, null, null);

        productService.processProduct(product);

        assertEquals(0, product.getAvailable());
        verify(productRepository).save(product);
        verify(notificationService).sendExpirationNotification(product.getName(), expiryDate);
    }

    @Test
    void notifiesDelayInsteadOfExpirationForUnexpiredProductWithoutStock() {
        Product product = product("EXPIRABLE", 3, 0, TODAY.plusDays(2), null, null);

        productService.processProduct(product);

        verify(notificationService).sendDelayNotification(3, product.getName());
        verify(notificationService, never()).sendExpirationNotification(product.getName(), product.getExpiryDate());
        verify(productRepository).save(product);
    }

    @Test
    void ignoresUnknownProductTypes() {
        Product product = product("UNKNOWN", 3, 1, null, null, null);

        productService.processProduct(product);

        assertEquals(1, product.getAvailable());
        verifyNoInteractions(notificationService, productRepository);
    }

    @Test
    void ignoresNullProduct() {
        productService.processProduct(null);

        verifyNoInteractions(notificationService, productRepository);
    }

    private static Product product(String type, Integer leadTime, Integer available,
            LocalDate expiryDate, LocalDate seasonStartDate, LocalDate seasonEndDate) {
        return new Product(null, leadTime, available, type, "Test product", expiryDate, seasonStartDate,
                seasonEndDate);
    }
}
