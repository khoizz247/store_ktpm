# Kiểm chứng transaction và checkout cạnh tranh

## Phạm vi

Không thêm chức năng hay thay đổi code production của A/B/C/D5. Bộ test mới
`CartCheckoutTransactionTest` kiểm tra CartService → OrderService → JPA/JDBC
trên H2 riêng, tắt seed, không truy cập database Docker hoặc .env.

| Tình huống | Invariant cần giữ |
|---|---|
| Sản phẩm bị INACTIVE sau khi thêm giỏ | HTTP 404; giữ toàn bộ giỏ; không đơn/audit; kho không đổi |
| Dòng hàng cuối thiếu kho | HTTP 409; không mua một phần các dòng trước |
| Lỗi sau khi đã INSERT audit đầu tiên | Rollback đơn/items/kho/audit; giỏ vẫn còn |
| Lỗi sau khi đã DELETE giỏ | Khôi phục giỏ; rollback đơn/items/kho/audit |
| Hai checkout cùng giỏ | Một HTTP 201, một 409; đúng một đơn/SALE; trừ kho một lần |
| Hai customer mua cùng sản phẩm, kho chỉ đủ một người | Một 201, một 409; kho không âm; giỏ người thua còn nguyên |

Test cùng giỏ được lặp ba lần; tổng cộng lớp này có 8 lần thực thi test.
Các test quyền, giỏ trống, quantity, CRUD/owner và thành công tuần tự đã có
trong `CartApiIntegrationTest` D5, không cần chép lại vào D6.

## Vì sao đây là test rollback thật?

Test dùng các adapter database thật, không mock trả kết quả thành công.
Decorator chỉ có trong TestConfiguration và được chọn bằng @Primary:

- InventoryStore vẫn INSERT audit thật, sau đó cố tình ném exception.
- CartStore vẫn DELETE giỏ thật, sau đó cố tình ném exception.
- Probe ghi nhận đơn/audit/kho/giỏ bên trong transaction trước lỗi; test đọc
  lại database sau khi exception thoát để kiểm tra dữ liệu đã rollback.

Do đó, test không chỉ xác nhận một yêu cầu bị từ chối trước khi ghi dữ liệu.
Không thêm endpoint hoặc cờ gây lỗi nào vào ứng dụng production.

## Vì sao cần đồng bộ hai luồng?

Hai worker cùng gọi MockMvc, chạy filter JWT và controller/service thật.
CountDownLatch giữ checkout đầu tại thời điểm đã ghi audit nhưng chưa commit,
chờ cả hai worker bước vào đọc giỏ có khóa rồi mới thả transaction đầu.
Kiểm tra cả HTTP và dữ liệu cuối, không phụ thuộc tên exception do database trả.
HTTP 500, hai lần thành công, timeout hoặc exception không xử lý đều làm test fail.

H2 không phải MySQL: test này không chứng minh mọi lịch xen kẽ hoặc hành vi khóa
InnoDB. Đặc biệt, thêm/sửa dòng giỏ đồng thời checkout và tải kéo dài không được
coi là đã kiểm chứng chỉ bằng bài thử hai checkout.

## Chạy Java test

Trong terminal tại thư mục repository:

```powershell
.\mvnw.cmd -Dtest=CartCheckoutTransactionTest test
.\mvnw.cmd verify
```

D7 đã thêm Failsafe vào lifecycle: verify mặc định chạy cả các lớp Test qua
Surefire và các lớp IT qua Failsafe, gồm `ProductAuditAndSeedIT` của B. Dùng:

```powershell
.\mvnw.cmd verify
```

Không cần `-Dtest=*Test,*IT` nữa để tránh chạy IT ở cả hai plugin. Chi tiết cấu
hình D7 và giới hạn kiểm tra kiến trúc: [CI_ARCHITECTURE.md](CI_ARCHITECTURE.md).

## Smoke hai checkout trên MySQL thật

Mở Docker Desktop, chạy lại image từ code hiện tại rồi kiểm tra:

```powershell
docker compose up --build -d --wait --wait-timeout 240
python scripts/verify_mysql.py --base-url http://localhost:18082
python scripts/verify_mysql.py --base-url http://localhost:18082 --exercise-cart-concurrently
```

Nếu API_HOST_PORT của bạn là 18081 thì thay base-url tương ứng. Không sửa .env
chỉ để khớp ví dụ; không dùng down -v vì sẽ xóa dữ liệu MySQL.

Script chọn sản phẩm ACTIVE đủ hai hàng, tạo customer mới và thêm quantity=2.
Hai HTTP POST checkout dùng cùng token, cùng giỏ và được khởi động đồng thời.
Script yêu cầu kết quả 201/409, một đơn PENDING đúng tổng tiền, một order item,
một SALE đúng actor và stock_before/after; kho chỉ giảm 2, giỏ rỗng.

**Có ghi dữ liệu:** mỗi lần chạy thành công giữ lại customer, một đơn/audit và
giảm kho 2. Không tự hủy/xóa. Chạy trên database demo, không có người khác đang
mua/điều chỉnh sản phẩm để so sánh stock_before chính xác. Thất bại có thể vẫn
để lại dữ liệu đã commit; kiểm tra log/database trước khi chạy lại.

Đây là smoke MySQL hai request, không phải Testcontainers, không chèn lỗi vào
MySQL, không phải kiểm thử tải Kaggle và không chứng minh mọi trường hợp cạnh tranh.

Python unit test của script dùng mock và không kết nối MySQL:

```powershell
python -m unittest discover -s scripts -p test_verify_mysql.py
```

Chỉ ghi nhận Java/MySQL PASS khi đã thực sự chạy được lệnh tương ứng. Thành công
trước đó của D5 không phải bằng chứng cho các test D6 mới.
