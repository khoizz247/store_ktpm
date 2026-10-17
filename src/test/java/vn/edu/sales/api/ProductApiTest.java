package vn.edu.sales.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.sales.application.port.out.ProductRepository;
import vn.edu.sales.domain.model.Product;
import vn.edu.sales.domain.model.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:b6-product-api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=false"
})
@AutoConfigureMockMvc
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class ProductApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProductRepository products;

    private Product prod1;
    private Product prod2;
    private Product prod3;
    private Product prod4;
    private Product prod5;
    private Product inactiveProd;

    @BeforeEach
    void prepareData() {
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }

        // Tạo 5 sản phẩm ACTIVE
        prod1 = products.save(new Product(null, "PROD-01", "Laptop Dell XPS 13", "Laptop cao cấp",
                new BigDecimal("30000000"), 5, ProductStatus.ACTIVE, null));
        prod2 = products.save(new Product(null, "PROD-02", "Laptop Lenovo ThinkPad", "Laptop doanh nhân",
                new BigDecimal("25000000"), 10, ProductStatus.ACTIVE, null));
        prod3 = products.save(new Product(null, "PROD-03", "Bàn phím cơ FL-Esports", "Bàn phím cơ không dây",
                new BigDecimal("1500000"), 15, ProductStatus.ACTIVE, null));
        prod4 = products.save(new Product(null, "PROD-04", "Chuột gaming Razer Viper", "Chuột siêu nhẹ",
                new BigDecimal("1200000"), 20, ProductStatus.ACTIVE, null));
        prod5 = products.save(new Product(null, "PROD-05", "Tai nghe không dây Sony WH", "Tai nghe chống ồn",
                new BigDecimal("6000000"), 8, ProductStatus.ACTIVE, null));

        // Tạo 1 sản phẩm INACTIVE
        inactiveProd = products.save(new Product(null, "PROD-06", "Laptop Toshiba cũ hỏng", "Laptop rã xác",
                new BigDecimal("500000"), 0, ProductStatus.INACTIVE, null));
    }

    @Test
    void getAllProductsWithoutAuthReturnsPublicPagedMetadata() throws Exception {
        mvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void getAllProductsPaginatesAccuratelyAcrossPages() throws Exception {
        // Trang 1 (phần tử thứ 3 và 4)
        mvc.perform(get("/api/products")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2));

        // Trang 2 (phần tử thứ 5)
        mvc.perform(get("/api/products")
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void getAllProductsBeyondLastPageReturnsEmptyListWithAccurateMetadata() throws Exception {
        mvc.perform(get("/api/products")
                        .param("page", "10")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(10))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void searchEndpointReturnsFilteredMetadataAndExcludesInactive() throws Exception {
        // Tìm "Laptop": có 2 sản phẩm ACTIVE (Dell XPS, Lenovo ThinkPad). Laptop Toshiba là INACTIVE nên bị loại trừ.
        mvc.perform(get("/api/products/search")
                        .param("q", "Laptop")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchWithEmptyOrWhitespaceQueryReturnsAllActiveMetadata() throws Exception {
        // Chuỗi rỗng
        mvc.perform(get("/api/products/search")
                        .param("q", "")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(5));

        // Khoảng trắng
        mvc.perform(get("/api/products/search")
                        .param("q", "   ")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(5));
    }

    @Test
    void searchBoundarySizesAndInvalidPagination() throws Exception {
        // Biên hợp lệ: size = 1
        mvc.perform(get("/api/products/search")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.items.length()").value(1));

        // Biên hợp lệ: size = 100
        mvc.perform(get("/api/products/search")
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(5));

        // Phân trang không hợp lệ: page < 0
        mvc.perform(get("/api/products/search")
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // Phân trang không hợp lệ: size = 0
        mvc.perform(get("/api/products/search")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // Phân trang không hợp lệ: size > 100
        mvc.perform(get("/api/products/search")
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getByIdReturnsActiveProductAndReturns404ForInactive() throws Exception {
        // Sản phẩm ACTIVE: 200 OK
        mvc.perform(get("/api/products/{id}", prod1.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(prod1.sku()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Sản phẩm INACTIVE: 404 Not Found
        mvc.perform(get("/api/products/{id}", inactiveProd.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
