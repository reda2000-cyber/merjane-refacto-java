package com.nimbleways.springboilerplate.services.implementations;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;

@Service
public class ProductService {

    private static final String NORMAL = "NORMAL";
    private static final String SEASONAL = "SEASONAL";
    private static final String EXPIRABLE = "EXPIRABLE";

    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, NotificationService notificationService, Clock clock) {
        this.productRepository = productRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public void processProduct(Product product) {
        if (product == null) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        if (NORMAL.equals(product.getType())) {
            processNormalProduct(product);
        } else if (SEASONAL.equals(product.getType())) {
            processSeasonalProduct(product, today);
        } else if (EXPIRABLE.equals(product.getType())) {
            processExpirableProduct(product, today);
        }
    }

    public void notifyDelay(int leadTime, Product product) {
        product.setLeadTime(leadTime);
        productRepository.save(product);
        notificationService.sendDelayNotification(leadTime, product.getName());
    }

    private void processNormalProduct(Product product) {
        if (hasAvailableStock(product)) {
            decrementStock(product);
        } else {
            notifyDelay(product);
        }
    }

    private void processSeasonalProduct(Product product, LocalDate today) {
        if (isInSeason(product, today) && hasAvailableStock(product)) {
            decrementStock(product);
            return;
        }

        if (isUnavailableForSeason(product, today)) {
            notificationService.sendOutOfStockNotification(product.getName());
            return;
        }

        notifyDelay(product);
    }

    private void processExpirableProduct(Product product, LocalDate today) {
        if (isExpired(product, today)) {
            notificationService.sendExpirationNotification(product.getName(), product.getExpiryDate());
            if (product.getAvailable() == null || product.getAvailable() != 0) {
                product.setAvailable(0);
                productRepository.save(product);
            }
            return;
        }

        if (hasAvailableStock(product)) {
            decrementStock(product);
        } else {
            notifyDelay(product);
        }
    }

    private boolean isInSeason(Product product, LocalDate today) {
        LocalDate seasonStartDate = product.getSeasonStartDate();
        LocalDate seasonEndDate = product.getSeasonEndDate();
        return seasonStartDate != null && seasonEndDate != null
                && !today.isBefore(seasonStartDate)
                && !today.isAfter(seasonEndDate);
    }

    private boolean isUnavailableForSeason(Product product, LocalDate today) {
        LocalDate seasonStartDate = product.getSeasonStartDate();
        LocalDate seasonEndDate = product.getSeasonEndDate();
        if (seasonStartDate == null || seasonEndDate == null || today.isBefore(seasonStartDate)) {
            return true;
        }

        LocalDate expectedRestockDate = today.plusDays(getLeadTime(product));
        return expectedRestockDate.isAfter(seasonEndDate);
    }

    private boolean isExpired(Product product, LocalDate today) {
        LocalDate expiryDate = product.getExpiryDate();
        return expiryDate == null || today.isAfter(expiryDate);
    }

    private boolean hasAvailableStock(Product product) {
        return product.getAvailable() != null && product.getAvailable() > 0;
    }

    private void decrementStock(Product product) {
        product.setAvailable(product.getAvailable() - 1);
        productRepository.save(product);
    }

    private void notifyDelay(Product product) {
        int leadTime = getLeadTime(product);
        if (leadTime > 0) {
            notifyDelay(leadTime, product);
        }
    }

    private int getLeadTime(Product product) {
        return product.getLeadTime() == null ? 0 : product.getLeadTime();
    }
}
