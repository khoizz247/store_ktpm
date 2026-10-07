package vn.edu.sales.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryStore {
    void record(Long productId, Long orderId, String type, int quantityChange, int stockBefore,
                int stockAfter, String referenceCode, String note, Long actorUserId);

    List<Movement> byProduct(Long productId);

    record Movement(Long id, Long productId, Long orderId, String type, int quantityChange,
                    int stockBefore, int stockAfter, String referenceCode, String note,
                    Long actorUserId, LocalDateTime createdAt) {
    }
}
