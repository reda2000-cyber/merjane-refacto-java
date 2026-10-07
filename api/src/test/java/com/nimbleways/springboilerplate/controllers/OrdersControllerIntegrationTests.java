package com.nimbleways.springboilerplate.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.NotificationService;

@SpringBootTest
@AutoConfigureMockMvc
class OrdersControllerIntegrationTests {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void processOrderDecrementsEligibleProductsAcrossAllTypes() throws Exception {
        Product normal = saveProduct("NORMAL", "normal-in-stock", 2, 1, null, null, null);
        Product seasonal = saveProduct("SEASONAL", "seasonal-in-stock", 1, 1, null, TODAY.minusDays(1),
                TODAY.plusDays(1));
        Product expirable = saveProduct("EXPIRABLE", "expirable-in-stock", 2, 1, TODAY.plusDays(1), null, null);

        processOrder(normal, seasonal, expirable).andExpect(status().isOk());

        assertEquals(0, available("normal-in-stock"));
        assertEquals(0, available("seasonal-in-stock"));
        assertEquals(0, available("expirable-in-stock"));
        verify(notificationService, never()).sendExpirationNotification("expirable-in-stock", TODAY.plusDays(1));
    }

    @Test
    void normalProductWithoutStockAnnouncesLeadTime() throws Exception {
        Product product = saveProduct("NORMAL", "normal-out-of-stock", 7, 0, null, null, null);

        processOrder(product).andExpect(status().isOk());

        verify(notificationService).sendDelayNotification(7, "normal-out-of-stock");
        assertEquals(0, available("normal-out-of-stock"));
    }

    @Test
    void seasonalProductAnnouncesDelayWhenRestockFitsTheSeason() throws Exception {
        Product product = saveProduct("SEASONAL", "seasonal-restock-in-time", 2, 0, null, TODAY.minusDays(1),
                TODAY.plusDays(5));

        processOrder(product).andExpect(status().isOk());

        verify(notificationService).sendDelayNotification(2, "seasonal-restock-in-time");
        verify(notificationService, never()).sendOutOfStockNotification("seasonal-restock-in-time");
    }

    @Test
    void seasonalProductIsUnavailableWhenRestockMissesTheSeason() throws Exception {
        Product product = saveProduct("SEASONAL", "seasonal-restock-too-late", 6, 0, null, TODAY.minusDays(1),
                TODAY.plusDays(5));

        processOrder(product).andExpect(status().isOk());

        verify(notificationService).sendOutOfStockNotification("seasonal-restock-too-late");
        verify(notificationService, never()).sendDelayNotification(6, "seasonal-restock-too-late");
    }

    @Test
    void seasonalProductCanBeSoldOnFirstDayOfItsSeason() throws Exception {
        Product product = saveProduct("SEASONAL", "seasonal-first-day", 1, 3, null, TODAY, TODAY.plusDays(5));

        processOrder(product).andExpect(status().isOk());

        assertEquals(2, available("seasonal-first-day"));
        verify(notificationService, never()).sendDelayNotification(1, "seasonal-first-day");
        verify(notificationService, never()).sendOutOfStockNotification("seasonal-first-day");
    }

    @Test
    void expirableProductCanBeSoldOnItsExpiryDate() throws Exception {
        Product product = saveProduct("EXPIRABLE", "expires-today", 2, 3, TODAY, null, null);

        processOrder(product).andExpect(status().isOk());

        assertEquals(2, available("expires-today"));
        verify(notificationService, never()).sendExpirationNotification("expires-today", TODAY);
    }

    @Test
    void unexpiredExpirableProductWithoutStockAnnouncesLeadTime() throws Exception {
        Product product = saveProduct("EXPIRABLE", "expirable-out-of-stock", 3, 0, TODAY.plusDays(10), null, null);

        processOrder(product).andExpect(status().isOk());

        verify(notificationService).sendDelayNotification(3, "expirable-out-of-stock");
        verify(notificationService, never()).sendExpirationNotification("expirable-out-of-stock", TODAY.plusDays(10));
    }

    @Test
    void expiredProductIsUnavailableAndNotifiesCustomer() throws Exception {
        Product product = saveProduct("EXPIRABLE", "already-expired", 3, 4, TODAY.minusDays(1), null, null);

        processOrder(product).andExpect(status().isOk());

        assertEquals(0, available("already-expired"));
        verify(notificationService).sendExpirationNotification("already-expired", TODAY.minusDays(1));
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/processOrder", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    private Product saveProduct(String type, String name, int leadTime, int stock, LocalDate expiryDate,
            LocalDate seasonStart, LocalDate seasonEnd) {
        return productRepository.save(new Product(null, leadTime, stock, type, name, expiryDate, seasonStart,
                seasonEnd));
    }

    private int available(String name) {
        return productRepository.findFirstByName(name).orElseThrow().getAvailable();
    }

    private org.springframework.test.web.servlet.ResultActions processOrder(Product... products) throws Exception {
        Order order = new Order();
        order.setItems(Set.of(products));
        Order savedOrder = orderRepository.save(order);
        return mockMvc.perform(post("/orders/{orderId}/processOrder", savedOrder.getId()));
    }
}
