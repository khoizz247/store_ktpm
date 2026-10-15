# Adapter giỏ hàng và tìm kiếm đơn

Gói này bổ sung CartStore, JdbcCartStore và JdbcOrderSearchStore. Chưa thêm
CartService/CartController hay endpoint mới; đó là phần tích hợp API sau.

## Giỏ hàng

- findByUser chỉ đọc các dòng có user_id được truyền vào, sắp theo product ID.
- Giá, tên, trạng thái và stock được đọc hiện tại từ products, chưa phải snapshot đơn.
- put đặt quantity tuyệt đối, không cộng thêm. Không trừ kho hoặc ghi ledger.
- remove/clear chỉ tác động user đã chỉ định; gọi lặp không ảnh hưởng giỏ khác.
- findByUserForUpdate phải được gọi trong TransactionRunner khi cần giữ khóa.
- Adapter không tự xác thực JWT/role; service/controller phải lấy owner từ danh tính
  đã xác thực, không cho client tùy chọn user ID để đọc giỏ người khác.

## Tìm kiếm đơn

OrderSearchStore.search nhận customerUserId (null nghĩa là không giới hạn owner),
status tùy chọn, code và page/size. Thứ tự created_at DESC, id DESC để phân trang
ổn định khi các đơn có cùng timestamp. Count áp dụng cùng bộ lọc với danh sách ID.
page >= 0, size từ 1 đến 100; code null được xem như rỗng. Dấu % và _ trong
code giữ ngữ nghĩa wildcard SQL LIKE của bản tham chiếu. Giá trị query được bind
qua placeholder, không ghép nội dung client trực tiếp vào SQL.

Service customer luôn phải truyền ID của chính customer; chỉ luồng admin được
truyền null. C4 cần dùng bean OrderSearchStore và mở rộng OrderService hiện tại
để trả trang đầy đủ; việc có adapter chưa có nghĩa API search đã hoàn tất.

## Kiểm thử

CartAndOrderSearchPersistenceTest dùng H2 riêng, tắt seed và bean thật. Bao phủ
mapping, owner isolation, quantity tuyệt đối, không giữ hàng, remove/clear lặp,
rollback, CHECK/FK, bộ lọc kết hợp, count/pagination/thứ tự, SQL parameters và
giới hạn page/size. Hai test còn kiểm chứng C3 tạo đơn và ghi kho/audit thực,
đơn tạo được tìm thấy qua adapter D3; thiếu kho không lưu dữ liệu một phần.

Chưa chứng minh locking/concurrency trên MySQL; checkout đồng thời thuộc D6/C7.
Thao tác put kiểm tra tồn tại rồi INSERT/UPDATE như bản nguồn; hai request thêm
cùng dòng mới có thể xung đột unique key. Không coi adapter này là bằng chứng
checkout đồng thời đã được xử lý.

## Nguồn tái sử dụng

Port và hai JDBC adapter dựa trên single-store-service. Bổ sung phòng vệ
pagination/null code và test tích hợp cho repository nhóm; không copy controller
hoặc service giỏ vào gói D3.
