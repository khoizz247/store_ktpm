package vn.edu.sales.api.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.sales.application.port.out.InventoryStore;
import vn.edu.sales.application.service.InventoryService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products/{productId}/inventory")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }

    @GetMapping
    public List<InventoryStore.Movement> movements(@PathVariable Long productId) {
        return service.byProduct(productId);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryStore.Movement adjust(Authentication authentication, @PathVariable Long productId,
                                          @Valid @RequestBody AdjustmentRequest request) {
        return service.adjust(authentication.getName(), productId, request.quantityChange(), request.note());
    }

    public record AdjustmentRequest(@NotNull Integer quantityChange, @Size(max = 500) String note) {}
}
