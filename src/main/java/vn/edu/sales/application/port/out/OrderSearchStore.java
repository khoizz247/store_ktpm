package vn.edu.sales.application.port.out;

import vn.edu.sales.domain.model.OrderStatus;

import java.util.List;

public interface OrderSearchStore {
    record Result(List<Long> orderIds, long totalElements) {}
    Result search(Long customerUserId, OrderStatus status, String code, int page, int size);
}
