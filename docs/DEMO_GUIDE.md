# Chạy demo nghiệm thu

Hai luồng bổ sung cho nhau, không phải hai API tạo ADMIN:

- [order-demo.http](order-demo.http): phần C8, đặt đơn trực tiếp, hai khách hàng,
  kiểm tra chủ sở hữu, admin xem chi tiết, hủy và hoàn kho đúng một lần.
- [checkout-demo.http](checkout-demo.http): giỏ → checkout → hủy; có thêm
  tìm kiếm, phân quyền, validation và soft delete sản phẩm demo.

File đã đổi tên để không còn `DEMO.http`/`demo.http` trùng nhau trên Windows.
Giữ nguyên nghiệp vụ của phần C8; sửa bước tạo ADMIN sai và thay placeholder bằng biến.

## 1. Chọn đúng phiên API và tài khoản

H2: chạy ứng dụng không bật profile MySQL; chọn `h2` trong **Run with** của HTTP
Client. File [http-client.env.json](http-client.env.json) chỉ chứa tài khoản seed
demo công khai `admin@example.com` / `Admin@123`, API cổng 18080. H2 là RAM, dữ
liệu mất khi dừng ứng dụng.

MySQL Docker: giữ `.env` đang dùng, không đổi mật khẩu/cổng để khớp mẫu. Xem
[DOCKER_MYSQL.md](DOCKER_MYSQL.md). Tạo riêng `docs/http-client.private.env.json`
bằng **Run with → Add Environment to Private File**, đặt môi trường `docker`:

```json
{
  "docker": {
    "base": "http://localhost:18082",
    "adminEmail": "THAY_BANG_EMAIL_ADMIN_DA_BOOTSTRAP",
    "adminPassword": "THAY_BANG_MAT_KHAU_ADMIN_CUA_BAN"
  }
}
```

Đổi `base` theo `API_HOST_PORT` thực tế (Compose mặc định 18081; máy demo có thể
dùng 18082), điền tài khoản ADMIN **đã tồn tại trong database đó**, chọn `docker`.
File private và `.env` đều bị Git bỏ qua. Không đưa mật khẩu thật vào `.http`
hoặc file môi trường public; không gửi token qua chat/ảnh chụp.
HTTP Client không tự đọc `.env` của Docker.

ADMIN MySQL được bootstrap trước khi có tài khoản nào trong bảng `users`.
`POST /api/auth/register` luôn tạo CUSTOMER. Nếu database đã có người dùng,
đổi BOOTSTRAP_ADMIN_EMAIL/PASSWORD không tạo thêm admin, không tự đặt lại mật khẩu
và không nâng quyền customer. Dùng tài khoản admin đã khởi tạo; nếu mất tài khoản,
dừng demo để xử lý riêng, không xóa volume/schema để chữa lỗi đăng nhập.

## 2. Chạy từng bước

1. Chỉ dùng database demo của mình; các request tạo user, sản phẩm, đơn và audit.
   Chờ API healthy rồi mở một trong hai file `.http` trong IntelliJ HTTP Client.
2. Chọn `h2` hoặc `docker`. Đổi email khách hàng và SKU sang giá trị chưa dùng
   trước mỗi lượt chạy độc lập; hai file dùng dữ liệu riêng để không đụng nhau.
3. Chạy từ trên xuống bằng nút cạnh từng request. Dừng ngay nếu bước trước báo
   fail; không dùng token/ID còn lại từ lần chạy cũ. Handler tự lưu JWT/ID khi
   bước tạo/đăng nhập thành công, không cần copy token thủ công.
4. Trong luồng C8, kiểm tra: ADMIN login → tạo kho 10 → A đặt 2 → kho 8 →
   B xem/hủy bị 404 → kho vẫn 8 → ADMIN xem được → A hủy → hủy lại 409 → kho 10.
   Audit phải có SALE -2, SALE_REVERSAL +2 và stockAfter = stockBefore + quantityChange.
5. Luồng checkout dùng giá 120000, số lượng 2: tổng 240000; kho 10 → 8 → 10.
   DELETE cuối chỉ soft delete sản phẩm do chính lượt demo tạo, không xóa lịch sử đơn.

## 3. Đọc lỗi đúng

| Hiện tượng | Kiểm tra |
|---|---|
| Connection refused | API đã chạy chưa, đúng port/profile chưa |
| ADMIN login 401 | Tài khoản thuộc DB đang chạy không; H2 seed khác MySQL bootstrap |
| Tạo sản phẩm 403 | JWT phải từ ADMIN login, không phải register customer |
| Register/SKU 409 | Dữ liệu đã tồn tại; đổi email/SKU, không xóa DB |
| B xem/hủy đơn của A 404 | Kết quả mong đợi, tránh tiết lộ đơn người khác |
| Hủy lần hai 409 | Kết quả mong đợi; kho không được tăng lần nữa |
| Biến chưa có giá trị | Chọn đúng environment và chạy bước tạo/login thành công trước |

Demo thủ công bổ sung, không thay thế Maven verify, CI MySQL hoặc báo cáo tải.
Cơ chế biến/môi trường private: [JetBrains HTTP Client variables](https://www.jetbrains.com/help/idea/http-client-variables.html).
