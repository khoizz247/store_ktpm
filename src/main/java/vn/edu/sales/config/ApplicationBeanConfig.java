package vn.edu.sales.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.sales.application.port.out.PasswordHasher;
import vn.edu.sales.application.port.out.TokenProvider;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.application.services.AuthService;
import vn.edu.sales.application.port.out.OrderRepository;
import vn.edu.sales.application.services.OrderService;

@Configuration
public class ApplicationBeanConfig {
    @Bean
    OrderService orderService(OrderRepository orderRepository) {
        return new OrderService(orderRepository);
    }

    @Bean
    AuthService authService(UserRepository users, PasswordHasher passwords, TokenProvider tokens) {
        return new AuthService(users, passwords, tokens);
    }
}
