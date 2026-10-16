package vn.edu.sales.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.sales.application.port.out.*;
import vn.edu.sales.application.service.ProductService;
import vn.edu.sales.application.services.AuthService;
import vn.edu.sales.application.services.OrderService;

@Configuration
public class ApplicationBeanConfig {
    @Bean
    ProductService productService(ProductRepository products, InventoryStore inventory,
                                  TransactionRunner transactions, UserRepository users) {
        return new ProductService(products, inventory, transactions, users);
    }

    @Bean
    OrderService orderService(OrderRepository orderRepository, ProductRepository productRepository,
                              UserRepository userRepository, TransactionRunner transactionRunner,
                              InventoryStore inventoryStore, OrderSearchStore orderSearchStore) {
        return new OrderService(orderRepository, productRepository, userRepository,
                transactionRunner, inventoryStore, orderSearchStore);
    }

    @Bean
    AuthService authService(UserRepository users, PasswordHasher passwords, TokenProvider tokens) {
        return new AuthService(users, passwords, tokens);
    }
}
