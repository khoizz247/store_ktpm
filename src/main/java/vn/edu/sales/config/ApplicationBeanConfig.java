package vn.edu.sales.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.sales.application.services.OrderService;

@Configuration
public class ApplicationBeanConfig {
    @Bean
    OrderService orderService() {
        return new OrderService();
    }
}
