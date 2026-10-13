package vn.edu.sales.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.sales.application.port.out.*;
import vn.edu.sales.application.services.OrderService;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.model.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final InventoryStore inventory = mock(InventoryStore.class);
    private OrderService service;

    private final Product keyboard = new Product(11L, "KB-11", "Bàn phím", "",
            new BigDecimal("125.50"), 5, ProductStatus.ACTIVE, 0L);

    @BeforeEach
    void setUp() {
        TransactionRunner transactions = new TransactionRunner() {
            @Override public <T> T execute(Supplier<T> work) { return work.get(); }
        };
        service = new OrderService(orders, products, users, transactions, inventory);
        when(users.findByEmail("customer@example.com")).thenReturn(Optional.of(new User(3L,
                "customer@example.com", "hash", "Customer", Role.CUSTOMER, UserStatus.ACTIVE, null)));
    }

    @Test
    void createsOrderWithServerCalculatedTotalAndDecreasesStock() {
        when(products.findAllByIdsForUpdate(List.of(11L))).thenReturn(List.of(keyboard));
        when(orders.save(any(Order.class))).thenAnswer(call -> {
            Order input = call.getArgument(0);
            return new Order(21L, input.orderCode(), input.userId(), input.recipientName(),
                    input.recipientPhone(), input.shippingAddress(), input.status(), input.totalAmount(),
                    input.createdAt(), input.updatedAt(), input.items());
        });

        Order result = service.create("customer@example.com", "Người nhận", "0901234567", "Hà Nội",
                List.of(new OrderService.CreateLine(11L, 2)));

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.totalAmount()).isEqualByComparingTo("251.00");
        assertThat(result.items().getFirst().productName()).isEqualTo("Bàn phím");
        assertThat(result.items().getFirst().unitPrice()).isEqualByComparingTo("125.50");
        verify(products).saveAll(argThat(list -> list.size() == 1 && list.getFirst().stockQuantity() == 3));
        verify(inventory).record(eq(11L), eq(21L), eq("SALE"), eq(-2), eq(5), eq(3),
                anyString(), eq("Đặt hàng"), eq(3L));
    }

    @Test
    void insufficientStockDoesNotSaveOrderOrChangeInventory() {
        when(products.findAllByIdsForUpdate(List.of(11L))).thenReturn(List.of(keyboard));

        assertThatThrownBy(() -> service.create("customer@example.com", "Người nhận", "0901234567",
                "Hà Nội", List.of(new OrderService.CreateLine(11L, 6))))
                .isInstanceOf(BusinessConflictException.class);

        verify(products, never()).saveAll(anyList());
        verifyNoInteractions(orders, inventory);
    }
}
