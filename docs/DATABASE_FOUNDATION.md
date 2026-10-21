# Nền tảng dữ liệu và transaction

## H2 mặc định

Chạy `./mvnw verify` (Windows: `.\mvnw.cmd verify`). Không bật profile MySQL.
Hibernate tìm bốn entity `users`, `products`, `orders`, `order_items`, sau đó
Spring chạy `schema-h2.sql` để tạo `cart_items`, `inventory_movements`.
Thứ tự này được bảo đảm bằng `defer-datasource-initialization=true`.
Flyway tắt; seed demo bật khi chạy ứng dụng mặc định, nhưng tắt trong các
integration test nghiệp vụ. Dữ liệu H2 mất khi tiến trình kết thúc.

## Hai implementation mới

- `SpringTransactionRunner` thực hiện port `TransactionRunner` bằng
  `TransactionTemplate`. Runtime exception trong callback làm rollback.
- `JdbcInventoryStore` thực hiện `InventoryStore`: ghi biến động và đọc theo
  sản phẩm, sắp xếp `created_at,id`; ánh xạ nullable order/actor đúng kiểu Long.

Cả hai có annotation component/repository, không cần thêm bean thủ công.
InventoryStore chỉ ghi ledger, không tự cập nhật stock. Service gọi cập nhật
sản phẩm và ghi ledger trong cùng transaction để bảo đảm nhất quán.

## Kiểm thử

`InventoryPersistenceTest` dùng H2 riêng và các bean thật:

1. Khởi tạo đủ sáu bảng và inject đúng implementation.
2. Commit cập nhật kho qua JPA và audit qua JDBC trong cùng transaction.
3. Runtime exception rollback cả kho và audit.
4. CHECK sai phép cộng kho làm rollback cả thay đổi sản phẩm.
5. Từ chối quantity_change bằng 0, kho âm, FK không tồn tại và reference trùng.

Test không được bọc toàn bộ bằng transaction test, để quan sát commit/rollback
của chính TransactionRunner. Dữ liệu test được tạo riêng; không dùng mật khẩu thật.

## MySQL

`application-mysql.yml` giữ Flyway bật, SQL init tắt, Hibernate validate và
defer=false để Flyway chạy trước kiểm tra JPA. Cần DB_USERNAME/DB_PASSWORD khi
bật profile mysql. Compose và script kiểm tra MySQL đã có; xem DOCKER_MYSQL.md.
Không coi test H2 là bằng chứng đã kiểm thử MySQL ở phiên bản mới.

## Nguồn tái sử dụng

Hai implementation transaction/inventory được tích hợp từ dự án tham chiếu
single-store-service. Test adapter/rollback là phần bổ sung cho repository nhóm.
