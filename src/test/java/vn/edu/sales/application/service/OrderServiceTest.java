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
    private final OrderSearchStore searches = mock(OrderSearchStore.class);
    private OrderService service;

    private final Product keyboard = new Product(11L, "KB-11", "Bàn phím", "",
            new BigDecimal("125.50"), 5, ProductStatus.ACTIVE, 0L);
    private final Product mouse = new Product(5L, "MS-05", "Chuột", "",
            new BigDecimal("50.00"), 10, ProductStatus.ACTIVE, 0L);

    @BeforeEach
    void setUp() {
        TransactionRunner transactions = new TransactionRunner() {
            @Override
            public <T> T execute(Supplier<T> work) {
                return work.get();
            }
        };
        service = new OrderService(orders, products, users, transactions, inventory, searches);
        when(users.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(new User(3L, "customer@example.com", "hash",
                        "Customer", Role.CUSTOMER, UserStatus.ACTIVE, null)));
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

        Order result = service.create("customer@example.com", "Người nhận",
                "0901234567", "Hà Nội",
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

        assertThatThrownBy(() -> service.create("customer@example.com", "Người nhận",
                "0901234567", "Hà Nội",
                List.of(new OrderService.CreateLine(11L, 6))))
                .isInstanceOf(BusinessConflictException.class);

        verify(products, never()).saveAll(anyList());
        verifyNoInteractions(orders, inventory);
    }

    @Test
    void duplicateLinesThrowException() {
        assertThatThrownBy(() -> service.create("customer@example.com", "Người nhận",
                "0901234567", "Hà Nội",
                List.of(new OrderService.CreateLine(11L, 1),
                        new OrderService.CreateLine(11L, 2))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(products, orders, inventory);
    }

    @Test
    void inactiveCustomerThrowsException() {
        when(users.findByEmail("locked@example.com"))
                .thenReturn(Optional.of(new User(4L, "locked@example.com", "hash",
                        "Locked", Role.CUSTOMER, UserStatus.LOCKED, null)));

        assertThatThrownBy(() -> service.create("locked@example.com", "Người nhận",
                "0901234567", "Hà Nội",
                List.of(new OrderService.CreateLine(11L, 1))))
                .isInstanceOf(BusinessConflictException.class);

        verifyNoInteractions(products, orders, inventory);
    }

    @Test
    void productsLockedInAscendingOrder() {
        when(products.findAllByIdsForUpdate(List.of(5L, 11L))).thenReturn(List.of(mouse, keyboard));
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        service.create("customer@example.com", "Người nhận",
                "0901234567", "Hà Nội",
                List.of(new OrderService.CreateLine(11L, 1),
                        new OrderService.CreateLine(5L, 2)));

        verify(products).findAllByIdsForUpdate(List.of(5L, 11L));
    }

    @Test
    void cancellingRestoresStockOnlyOnce() {
        Order pending = order(OrderStatus.PENDING);
        Order cancelled = pending.withStatus(OrderStatus.CANCELLED);
        when(orders.findByIdForUpdate(21L)).thenReturn(Optional.of(pending), Optional.of(cancelled));
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        when(products.findAllByIdsForUpdate(List.of(11L))).thenReturn(List.of(keyboard));

        assertThat(service.changeStatus(21L, OrderStatus.CANCELLED).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(service.changeStatus(21L, OrderStatus.CANCELLED).status()).isEqualTo(OrderStatus.CANCELLED);

        verify(products, times(1)).saveAll(argThat(list -> list.getFirst().stockQuantity() == 7));
        verify(inventory, times(1)).record(eq(11L), eq(21L), eq("SALE_REVERSAL"), eq(2),
                eq(5), eq(7), anyString(), anyString(), isNull());
        verify(orders, times(1)).save(any(Order.class));
    }

    @Test
    void customerCancelsOnlyOwnPendingOrderAndInventoryRecordsActor() {
        Order pending = order(OrderStatus.PENDING);
        when(orders.findByIdForUpdate(21L)).thenReturn(Optional.of(pending));
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        when(products.findAllByIdsForUpdate(List.of(11L))).thenReturn(List.of(keyboard));

        assertThat(service.cancelMine("customer@example.com", 21L).status())
                .isEqualTo(OrderStatus.CANCELLED);
        verify(inventory).record(eq(11L), eq(21L), eq("SALE_REVERSAL"), eq(2), eq(5), eq(7),
                anyString(), anyString(), eq(3L));
    }

    @Test
    void invalidTransitionReturnsConflict() {
        when(orders.findByIdForUpdate(21L)).thenReturn(Optional.of(order(OrderStatus.PENDING)));
        assertThatThrownBy(() -> service.changeStatus(21L, OrderStatus.COMPLETED))
                .isInstanceOf(BusinessConflictException.class);

        verify(products, never()).saveAll(anyList());
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void adminCanCancelConfirmedOrderSuccessfully() {
        Order confirmed = order(OrderStatus.CONFIRMED);
        when(orders.findByIdForUpdate(21L)).thenReturn(Optional.of(confirmed));
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        when(products.findAllByIdsForUpdate(List.of(11L))).thenReturn(List.of(keyboard));

        Order result = service.changeStatus(21L, OrderStatus.CANCELLED);
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventory, times(1)).record(eq(11L), eq(21L),
                eq("SALE_REVERSAL"), anyInt(), anyInt(), anyInt(), anyString(), anyString(), isNull());
    }

    @Test
    void shippingToCancelledReturnsConflict() {
        Order shipping = order(OrderStatus.SHIPPING);
        when(orders.findByIdForUpdate(21L)).thenReturn(Optional.of(shipping));

        assertThatThrownBy(() -> service.changeStatus(21L, OrderStatus.CANCELLED))
                .isInstanceOf(BusinessConflictException.class);

        verify(products, never()).saveAll(anyList());
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void adminPatchSameStatusIsNoOpWithoutExtraStockRestoration() {
        Order pending = order(OrderStatus.PENDING);
        when(orders.findByIdForUpdate(21L)).thenReturn(Optional.of(pending));

        Order result = service.changeStatus(21L, OrderStatus.PENDING);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);

        verify(orders, never()).save(any(Order.class));
        verify(inventory, never()).record(anyLong(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(),
                anyString(), anyString(), any());
    }

    private Order order(OrderStatus status) {
        return new Order(21L, "ORD-21", 3L, "Người nhận",
                "0901234567", "Hà Nội", status,
                null, null, null,
                List.of(new OrderItem(1L, 11L, "Bàn phím",
                        new BigDecimal("125.50"), 2, null)));
    }
}
