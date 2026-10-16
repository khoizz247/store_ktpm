# Giỏ hàng và checkout D5

## Phạm vi và luồng xử lý

CartController trong api.system.order gọi CartService trong application.services.
ApplicationBeanConfig nối CartStore, UserRepository, ProductRepository, OrderService
và TransactionRunner đã có; không thêm framework web/database vào service.
SecurityConfig hiện đã yêu cầu CUSTOMER cho /api/cart và /api/cart/**.

Email được lấy từ Authentication, không nhận userId của giỏ từ client.
CartService còn kiểm tra tài khoản ACTIVE và role CUSTOMER trong DB.
Giá/tên trong giỏ được đọc hiện tại từ products; chỉ order_items mới là snapshot.

| API | Kết quả |
|---|---|
| GET /api/cart | 200 danh sách dòng giỏ hiện tại |
| PUT /api/cart/items/{productId} | 200 giỏ sau khi đặt quantity tuyệt đối |
| DELETE /api/cart/items/{productId} | 204 xóa một dòng của chính mình |
| DELETE /api/cart | 204 xóa giỏ của chính mình |
| POST /api/cart/checkout | 201 đơn PENDING và giỏ được xóa |

PUT quantity phải ít nhất 1, không vượt stock tại thời điểm thêm và sản phẩm ACTIVE.
Thêm giỏ không giữ hoặc trừ kho. Checkout kiểm tra sản phẩm/kho lại qua OrderService.
Checkout bọc đọc giỏ có khóa, tạo đơn và xóa giỏ trong cùng transaction; khi lỗi
không được xóa giỏ hay lưu một phần đơn/audit. Các yêu cầu mua đồng thời vẫn cần
kiểm thử riêng ở D6/C7, không suy ra chỉ từ kiểm thử tuần tự.

## Demo Swagger

Chạy app hoặc build lại Docker để endpoint mới có mặt. Dùng đúng cổng của phiên
demo, ví dụ Docker local hiện dùng 18082. Đăng nhập CUSTOMER, copy accessToken
vào HTTP Bearer Authorize (không dùng token ADMIN cho giỏ).

1. Chọn một productId ACTIVE còn ít nhất hai hàng qua GET /api/products.
2. PUT /api/cart/items/{productId}, body {"quantity":2}; Execute trả 200.
3. GET /api/cart thấy quantity=2; GET sản phẩm xác nhận stock chưa đổi.
4. POST /api/cart/checkout với body dưới; Execute trả 201.
5. GET giỏ trả []; GET sản phẩm thấy stock giảm 2; GET /api/orders/me thấy đơn.

```json
{
  "recipientName": "Khách demo",
  "recipientPhone": "0901234567",
  "shippingAddress": "123 Đường Mẫu, Hà Nội"
}
```

Không gửi price, totalAmount, status hoặc userId từ client. Giá do server quyết định.
Checkout giỏ trống trả 409; body thiếu thông tin/quantity null hoặc <=0 trả 400.
Không JWT trả 401, ADMIN dùng giỏ trả 403. Sản phẩm không có/INACTIVE trả 404;
thiếu kho trả 409. Customer khác không được tác động giỏ của mình.

C5 đã có endpoint PATCH /api/orders/{id}/cancel: nếu muốn hoàn lại hàng demo,
chỉ hủy đơn PENDING của chính tài khoản đã checkout. Hủy lặp không hoàn kho lần hai.

## Kiểm chứng

```powershell
.\mvnw.cmd verify
python -m unittest discover -s scripts -p test_verify_mysql.py
docker compose up --build -d --wait --wait-timeout 240
python scripts/verify_mysql.py --base-url http://localhost:18082
python scripts/verify_mysql.py --base-url http://localhost:18082 --exercise-cart
```

Flag --exercise-cart tạo một customer, một đơn mua hai hàng và giữ dữ liệu/audit.
Chạy mỗi lần tạo đơn mới và giảm kho thêm hai; không phải request idempotent.
Script mặc định không có flag chỉ kiểm tra readiness, không tạo dữ liệu.
Python unit test dùng mock, không thay thế kiểm chứng MySQL thật.

CartApiIntegrationTest dùng MockMvc + JWT thật + H2 riêng tắt seed; bao phủ 9 test
cho quyền, owner isolation, CRUD, không giữ hàng, checkout, validation, giỏ trống,
thiếu kho sau khi thêm và luồng checkout/hủy C5. Test concurrency/nhánh lỗi sâu
thuộc D6; không coi số test là bằng chứng tất cả tình huống đã được kiểm chứng.

## Nguồn tích hợp

CartService và CartController dựa trên single-store-service, đã sửa package và
wiring theo repository nhóm. API integration test và smoke MySQL là phần bổ sung.
