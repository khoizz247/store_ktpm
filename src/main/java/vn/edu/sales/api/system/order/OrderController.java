package vn.edu.sales.api.system.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.sales.application.services.OrderService;
import vn.edu.sales.domain.model.Order;
import vn.edu.sales.domain.model.OrderItem;
import vn.edu.sales.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(Authentication authentication, @Valid @RequestBody CreateOrderRequest request) {
        List<OrderService.CreateLine> lines = request.items().stream()
                .map(item -> new OrderService.CreateLine(item.productId(), item.quantity())).toList();
        return OrderResponse.from(orderService.create(authentication.getName(), request.recipientName(),
                request.recipientPhone(), request.shippingAddress(), lines));
    }

    @GetMapping("/me")
    public PagedOrders mine(Authentication authentication,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return PagedOrders.from(orderService.searchMine(authentication.getName(), null, "", page, size));
    }

    @GetMapping("/{id}")
    public OrderResponse detail(Authentication authentication, @PathVariable Long id) {
        return OrderResponse.from(orderService.getMineById(authentication.getName(), id));
    }

    @GetMapping("/me/search")
    public PagedOrders searchMine(Authentication authentication,
                                  @RequestParam(required = false) OrderStatus status,
                                  @RequestParam(defaultValue = "") String code,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return PagedOrders.from(orderService.searchMine(authentication.getName(), status, code, page, size));
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(Authentication authentication, @PathVariable Long id) {
        return OrderResponse.from(orderService.cancelMine(authentication.getName(), id));
    }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Size(max = 30) String recipientPhone,
            @NotBlank @Size(max = 500) String shippingAddress,
            @NotEmpty List<@NotNull @Valid CreateOrderItemRequest> items) {
    }

    public record CreateOrderItemRequest(@NotNull Long productId, @NotNull @Min(1) Integer quantity) {
    }

    public record OrderResponse(Long id, String orderCode, Long userId, String recipientName,
                                String recipientPhone, String shippingAddress, String status,
                                BigDecimal totalAmount, LocalDateTime createdAt, LocalDateTime updatedAt,
                                List<OrderItemResponse> items) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(order.id(), order.orderCode(), order.userId(), order.recipientName(),
                    order.recipientPhone(), order.shippingAddress(), order.status().name(), order.totalAmount(),
                    order.createdAt(), order.updatedAt(), order.items().stream().map(OrderItemResponse::from).toList());
        }
    }

    public record OrderItemResponse(Long id, Long productId, String productName, BigDecimal unitPrice,
                                    int quantity, BigDecimal lineTotal) {
        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.id(), item.productId(), item.productName(), item.unitPrice(),
                    item.quantity(), item.lineTotal());
        }
    }

    public record PagedOrders(List<OrderResponse> items, int page, int size,
                              long totalElements, int totalPages) {
        public static PagedOrders from(OrderService.OrderPage result) {
            return new PagedOrders(result.items().stream().map(OrderResponse::from).toList(),
                    result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
