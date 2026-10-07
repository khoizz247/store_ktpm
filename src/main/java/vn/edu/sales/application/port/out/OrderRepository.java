package vn.edu.sales.application.port.out;

import vn.edu.sales.domain.model.Order;
import vn.edu.sales.domain.model.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    Optional<Order> findByIdForUpdate(Long id);
    List<Order> findAllByUserId(Long userId);
    List<Order> findAll(OrderStatus status);
}
