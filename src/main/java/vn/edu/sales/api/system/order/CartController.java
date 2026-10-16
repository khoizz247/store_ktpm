package vn.edu.sales.api.system.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.sales.application.port.out.CartStore;
import vn.edu.sales.application.services.CartService;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) { this.service = service; }

    @GetMapping
    public List<CartStore.Line> mine(Authentication authentication) {
        return service.mine(authentication.getName());
    }

    @PutMapping("/items/{productId}")
    public List<CartStore.Line> put(Authentication authentication, @PathVariable Long productId,
                                    @Valid @RequestBody QuantityRequest request) {
        return service.put(authentication.getName(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication, @PathVariable Long productId) {
        service.remove(authentication.getName(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication authentication) { service.clear(authentication.getName()); }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderController.OrderResponse checkout(Authentication authentication,
                                                  @Valid @RequestBody CheckoutRequest request) {
        return OrderController.OrderResponse.from(service.checkout(authentication.getName(),
                request.recipientName(), request.recipientPhone(), request.shippingAddress()));
    }

    public record QuantityRequest(@NotNull @Min(1) Integer quantity) {}

    public record CheckoutRequest(@NotBlank @Size(max = 120) String recipientName,
                                  @NotBlank @Size(max = 30) String recipientPhone,
                                  @NotBlank @Size(max = 500) String shippingAddress) {}
}
