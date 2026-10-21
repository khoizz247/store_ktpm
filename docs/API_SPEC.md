# Đặc tả backend một cửa hàng

## Actor và phạm vi

- Khách chưa đăng nhập: xem/tìm sản phẩm ACTIVE, đăng ký và đăng nhập.
- CUSTOMER: tài khoản cá nhân, giỏ của mình, đặt/xem/tìm/hủy đơn của mình.
- ADMIN: quản lý sản phẩm/tồn kho, đọc mọi đơn và chuyển trạng thái.
- Hệ thống: xác thực JWT, tính tổng tiền, snapshot tên/giá, cập nhật kho/audit.

Không có quản lý chi nhánh, nhân viên theo phòng ban, nhà cung cấp hoặc thanh toán.
ADMIN không sử dụng API giỏ/đặt đơn của CUSTOMER. Không có API đăng ký ADMIN công khai.

## API và quyền

JSON request/response; token gửi bằng `Authorization: Bearer <JWT>`.
Swagger chọn Authorize và dán riêng token (không thêm chữ Bearer).

| Method | Endpoint | Quyền | Thành công |
|---|---|---|---|
| GET | /api/health | Public | 200 |
| POST | /api/auth/register | Public, tạo CUSTOMER | 201 + token |
| POST | /api/auth/login | Public | 200 + token |
| GET | /api/users/me | Đã đăng nhập | 200 |
| POST | /api/users/me/password | Đã đăng nhập | 204 |
| GET | /api/products | Public; page,size | 200 page |
| GET | /api/products/search | Public; q,page,size | 200 page |
| GET | /api/products/{id} | Public; ACTIVE | 200 |
| POST | /api/products | ADMIN | 201 |
| PUT | /api/products/{id} | ADMIN | 200 |
| PATCH | /api/products/{id}/stock | ADMIN | 200 |
| DELETE | /api/products/{id} | ADMIN; soft delete | 204 |
| GET | /api/admin/products/{productId}/inventory | ADMIN | 200 list |
| POST | /api/admin/products/{productId}/inventory/adjustments | ADMIN | 201 |
| GET | /api/cart | CUSTOMER, giỏ của mình | 200 list |
| PUT | /api/cart/items/{productId} | CUSTOMER | 200 list |
| DELETE | /api/cart/items/{productId} | CUSTOMER | 204 |
| DELETE | /api/cart | CUSTOMER | 204 |
| POST | /api/cart/checkout | CUSTOMER | 201 order |
| POST | /api/orders | CUSTOMER | 201 order |
| GET | /api/orders/me | CUSTOMER; page,size | 200 page |
| GET | /api/orders/me/search | CUSTOMER; status,code,page,size | 200 page |
| GET | /api/orders/{id} | CUSTOMER, đúng chủ đơn | 200 |
| PATCH | /api/orders/{id}/cancel | CUSTOMER, đúng chủ và PENDING | 200 |
| GET | /api/admin/orders | ADMIN; status,page,size | 200 page |
| GET | /api/admin/orders/search | ADMIN; status,code,page,size | 200 page |
| GET | /api/admin/orders/{id} | ADMIN | 200 |
| PATCH | /api/admin/orders/{id}/status | ADMIN | 200 |

Page bắt đầu từ 0, mặc định size=20, giới hạn 1–100. Page response:
`{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}`.
Search trả tập kết quả có lọc; detail theo ID trả đúng một tài nguyên hoặc 404.
Mã đơn là mã nghiệp vụ UNIQUE; ID là khóa kỹ thuật cho FK/API, không trùng chức năng.

## Quy tắc nghiệp vụ

- Sản phẩm: SKU không trùng, chuẩn hóa chữ hoa; giá >0, tối đa 13 chữ số phần
  nguyên và 2 chữ số thập phân; stock là số nguyên không âm, phải gửi rõ khi ghi.
  DELETE chuyển INACTIVE, giữ bản ghi và lịch sử đơn; API public không trả INACTIVE.
- Tạo đơn: items không rỗng, không null, không trùng productId; quantity>=1.
  Khóa sản phẩm theo ID tăng dần, kiểm tra ACTIVE/đủ kho, dùng giá từ database.
  Tên/giá được lưu snapshot ở order_items, không đổi theo sản phẩm về sau.
  Tổng tiền tối đa 9,999,999,999,999.99 theo DECIMAL(15,2).
- Đơn/kho/audit cùng transaction. Không đủ kho trả 409, không tạo đơn dở dang.
- Giỏ không giữ chỗ tồn kho. PUT quantity là đặt số lượng tuyệt đối, không cộng dồn.
  Checkout đọc/khóa giỏ, dùng lại tạo đơn, chỉ xóa giỏ khi transaction thành công.
- Audit có stock_before, quantity_change, stock_after và phải thỏa
  `stock_after = stock_before + quantity_change`. API ghi actor từ tài khoản đăng nhập.
  Audit seed/bootstrap có thể không có actor vì không phát sinh từ một request.
- Customer chỉ hủy PENDING. Hủy lại trả 409, không hoàn hai lần. Admin PATCH cùng
  trạng thái là no-op 200; không ghi audit hoặc hoàn kho lần nữa.
- Trạng thái: PENDING → CONFIRMED → SHIPPING → COMPLETED;
  PENDING hoặc CONFIRMED có thể → CANCELLED. Không hủy SHIPPING/terminal.
- updatedAt trong response thay đổi trạng thái lấy sau flush/refresh, khớp GET lại.
- Đăng ký mật khẩu 8–72 ký tự và tối đa 72 byte UTF-8. Đổi mật khẩu cần mật khẩu
  cũ đúng; mật khẩu mới >=12 ký tự, tối đa 72 byte UTF-8, hoa/thường/số/ký tự đặc
  biệt, khác mật khẩu cũ. Không có API nâng quyền thông qua đăng ký.

## Response lỗi

```json
{"timestamp":"2026-10-21T00:00:00Z","status":400,"code":"VALIDATION_ERROR",
 "message":"Dữ liệu không hợp lệ","path":"/api/orders","fields":{"items[0]":"must not be null"}}
```

| HTTP | Ý nghĩa/code thông dụng |
|---|---|
| 400 | VALIDATION_ERROR, INVALID_REQUEST: body/parameter không hợp lệ |
| 401 | UNAUTHORIZED: thiếu/sai/hết hạn JWT; INVALID_CREDENTIALS: đăng nhập sai |
| 403 | FORBIDDEN: có token nhưng sai role |
| 404 | RESOURCE_NOT_FOUND: không tồn tại/INACTIVE/không phải chủ đơn |
| 409 | BUSINESS_CONFLICT, DATA_CONFLICT, CONCURRENT_CHANGE |
| 500 | DATABASE_ERROR: lỗi truy cập DB; không trả SQL, mật khẩu, stack trace |

Response thành công giữ DTO riêng, list/page theo bảng; không bọc mọi response
trong một envelope chung. 204 không có body. Không khẳng định mọi lỗi framework
không xác định đều đã có ApiError; kiểm thử tập trung các nhánh công bố ở trên.

## Sáu bảng

users 1–N orders; orders 1–N order_items; products 1–N order_items.
cart_items có PK(user_id,product_id), FK tới users/products.
inventory_movements FK product, order nullable và actor nullable.
flyway_schema_history là bảng kỹ thuật, không tính là bảng nghiệp vụ thứ bảy.

## Giới hạn baseline

JWT chưa có thu hồi token/refresh/logout; token cũ chưa bị vô hiệu hóa ngay khi đổi
mật khẩu và role/status trong token có thể cũ cho tới hết hạn. Không dùng tài khoản
demo hoặc JWT secret H2 để triển khai production. Chưa có thanh toán, idempotency
key cho POST tạo đơn hay tối ưu N+1; chi tiết ảnh hưởng tại FINAL_REVIEW.md.
