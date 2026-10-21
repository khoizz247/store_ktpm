package vn.edu.sales.api.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.sales.application.service.ProductService;
import vn.edu.sales.application.port.out.ProductPage;
import vn.edu.sales.domain.model.Product;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PagedProducts getAll(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return search("", page, size);
    }

    @GetMapping("/search")
    public PagedProducts search(@RequestParam(defaultValue = "") String q,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        ProductPage result = productService.search(q, page, size);
        return new PagedProducts(result.items().stream().map(ProductResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    public record PagedProducts(List<ProductResponse> items, int page, int size,
                                long totalElements, int totalPages) {
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return ProductResponse.from(productService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(Authentication authentication, @Valid @RequestBody CreateProductRequest request) {
        return ProductResponse.from(productService.create(
                authentication.getName(), request.sku(), request.name(), request.description(), request.price(), request.stockQuantity()
        ));
    }

    @PutMapping("/{id}")
    public ProductResponse update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(productService.update(authentication.getName(), id, request.name(), request.description(),
                request.price(), request.stockQuantity()));
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse updateStock(Authentication authentication, @PathVariable Long id, @Valid @RequestBody UpdateStockRequest request) {
        return ProductResponse.from(productService.updateStock(authentication.getName(), id, request.stockQuantity()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity
    ) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity
    ) {
    }

    public record UpdateStockRequest(@NotNull @Min(0) Integer stockQuantity) {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            String description,
            BigDecimal price,
            int stockQuantity,
            String status,
            Long version
    ) {
        static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.id(), product.sku(), product.name(), product.description(), product.price(),
                    product.stockQuantity(), product.status().name(), product.version()
            );
        }
    }
}
