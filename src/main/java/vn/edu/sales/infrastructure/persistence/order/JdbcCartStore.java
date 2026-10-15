package vn.edu.sales.infrastructure.persistence.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.edu.sales.application.port.out.CartStore;

import java.util.List;

@Repository
public class JdbcCartStore implements CartStore {
    private static final String SELECT = "SELECT p.id AS product_id,p.sku,p.name,p.price,p.stock_quantity," +
            "p.status,ci.quantity FROM cart_items ci JOIN products p ON p.id=ci.product_id " +
            "WHERE ci.user_id=? ORDER BY p.id";
    private static final RowMapper<Line> MAPPER = (rs, row) -> new Line(
            rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"),
            rs.getBigDecimal("price"), rs.getInt("stock_quantity"), rs.getString("status"),
            rs.getInt("quantity"));
    private final JdbcTemplate jdbc;

    public JdbcCartStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Line> findByUser(Long userId) { return jdbc.query(SELECT, MAPPER, userId); }

    @Override
    public List<Line> findByUserForUpdate(Long userId) {
        return jdbc.query(SELECT + " FOR UPDATE", MAPPER, userId);
    }

    @Override
    public void put(Long userId, Long productId, int quantity) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE user_id=? AND product_id=?",
                Integer.class, userId, productId);
        if (existing != null && existing > 0) {
            jdbc.update("UPDATE cart_items SET quantity=?,updated_at=CURRENT_TIMESTAMP " +
                    "WHERE user_id=? AND product_id=?", quantity, userId, productId);
        } else {
            jdbc.update("INSERT INTO cart_items(user_id,product_id,quantity) VALUES(?,?,?)",
                    userId, productId, quantity);
        }
    }

    @Override
    public void remove(Long userId, Long productId) {
        jdbc.update("DELETE FROM cart_items WHERE user_id=? AND product_id=?", userId, productId);
    }

    @Override
    public void clear(Long userId) { jdbc.update("DELETE FROM cart_items WHERE user_id=?", userId); }
}
