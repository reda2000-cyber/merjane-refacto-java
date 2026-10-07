package com.nimbleways.springboilerplate.services.implementations;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.repositories.OrderRepository;

@Service
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final ProductService productService;

    public OrderProcessingService(OrderRepository orderRepository, ProductService productService) {
        this.orderRepository = orderRepository;
        this.productService = productService;
    }

    @Transactional
    public Optional<Long> processOrder(Long orderId) {
        return orderRepository.findById(orderId).map(this::process);
    }

    private Long process(Order order) {
        if (order.getItems() != null) {
            order.getItems().forEach(productService::processProduct);
        }
        return order.getId();
    }
}
