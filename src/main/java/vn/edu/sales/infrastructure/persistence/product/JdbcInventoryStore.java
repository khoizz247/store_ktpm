package vn.edu.sales.infrastructure.persistence.product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.sales.application.port.out.InventoryStore;

import java.util.List;

@Repository
public class JdbcInventoryStore implements InventoryStore {
    private final JdbcTemplate jdbc;

    public JdbcInventoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(Long productId, Long orderId, String type, int quantityChange, int stockBefore,
                       int stockAfter, String referenceCode, String note, Long actorUserId) {
        jdbc.update("INSERT INTO inventory_movements(product_id,order_id,movement_type,quantity_change," +
                        "stock_before,stock_after,reference_code,note,created_by_user_id) VALUES(?,?,?,?,?,?,?,?,?)",
                productId, orderId, type, quantityChange, stockBefore, stockAfter, referenceCode, note, actorUserId);
    }

    @Override
    public List<Movement> byProduct(Long productId) {
        return jdbc.query("SELECT id,product_id,order_id,movement_type,quantity_change,stock_before,stock_after," +
                "reference_code,note,created_by_user_id,created_at FROM inventory_movements " +
                "WHERE product_id=? ORDER BY created_at,id", (rs, row) -> new Movement(
                rs.getLong("id"), rs.getLong("product_id"), rs.getObject("order_id", Long.class),
                rs.getString("movement_type"), rs.getInt("quantity_change"), rs.getInt("stock_before"),
                rs.getInt("stock_after"), rs.getString("reference_code"), rs.getString("note"),
                rs.getObject("created_by_user_id", Long.class), rs.getTimestamp("created_at").toLocalDateTime()),
                productId);
    }
}
