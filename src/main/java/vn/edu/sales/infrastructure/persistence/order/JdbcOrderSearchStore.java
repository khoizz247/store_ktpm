package vn.edu.sales.infrastructure.persistence.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.sales.application.port.out.OrderSearchStore;
import vn.edu.sales.domain.model.OrderStatus;

import java.util.List;

@Repository
public class JdbcOrderSearchStore implements OrderSearchStore {
    private static final String FILTER = " FROM orders WHERE (? IS NULL OR user_id=?) " +
            "AND (? IS NULL OR status=?) AND (? = '' OR order_code LIKE ?)";
    private final JdbcTemplate jdbc;

    public JdbcOrderSearchStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Result search(Long customerUserId, OrderStatus status, String code, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page phải >= 0, size từ 1 đến 100");
        }
        String query = code == null ? "" : code;
        String state = status == null ? null : status.name();
        String pattern = "%" + query + "%";
        Object[] filters = {customerUserId, customerUserId, state, state, query, pattern};
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FILTER, Long.class, filters);
        List<Long> ids = jdbc.query("SELECT id" + FILTER + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> rs.getLong(1), customerUserId, customerUserId, state, state, query, pattern,
                size, (long) page * size);
        return new Result(ids, total == null ? 0 : total);
    }
}
