package vn.edu.sales.api.system.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import vn.edu.sales.application.services.OrderService;
import vn.edu.sales.domain.model.OrderStatus;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public OrderController.PagedOrders getAll(@RequestParam(required = false) OrderStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return OrderController.PagedOrders.from(orderService.searchAll(status, "", page, size));
    }

    @GetMapping("/{id}")
    public OrderController.OrderResponse detail(@PathVariable Long id) {
        return OrderController.OrderResponse.from(orderService.getAdminById(id));
    }

    @GetMapping("/search")
    public OrderController.PagedOrders search(@RequestParam(required = false) OrderStatus status,
                                              @RequestParam(defaultValue = "") String code,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return OrderController.PagedOrders.from(orderService.searchAll(status, code, page, size));
    }

    @PatchMapping("/{id}/status")
    public OrderController.OrderResponse updateStatus(Authentication authentication, @PathVariable Long id,
                                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return OrderController.OrderResponse.from(orderService.changeStatus(authentication.getName(), id, request.status()));
    }

    public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
    }
}
