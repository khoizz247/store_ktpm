# Rà soát chốt Pha 1 — 21/10/2026

## Kết luận

Đã cập nhật repository nhóm từ a157e79 tới origin/main **28e42d7** bằng
fast-forward, giữ phần D7 đang làm dở. Các nhánh remote được fetch đều đã merge
vào main tại thời điểm kiểm tra. Các sửa trong lượt rà soát được bàn giao trên
nhánh finalize/phase1-single-store; dùng SHA của commit trên nhánh đó để nghiệm thu,
không dùng SHA 28e42d7 để tuyên bố đã chứa những sửa mới. Không tự merge vào main.

Chức năng baseline một cửa hàng đã có trong code, không cần mở rộng thêm nghiệp
vụ để đạt đề bài. **CI đã xanh cho commit 941ec24a8158a5bf072d823a129abff5415d0c30**:
Maven verify/ArchUnit/Python và Compose MySQL/Flyway/checkout đồng thời đều pass.
Đã đối chiếu ZIP Kaggle gốc: CRC hợp lệ, metadata đúng commit, đủ bốn pha,
CSV/HTML/log khớp theo từng loại snapshot, không có failure/exception được ghi nhận.
**Đủ chức năng và minh chứng nghiệm thu Pha 1 theo đề bài đã cung cấp.**
Đây là baseline một cửa hàng, không phải tuyên bố tối ưu hoặc sẵn sàng production.
Báo cáo cuối và minh chứng: [performance/REPORT.md](../performance/REPORT.md).
Phần bàn giao Git còn push minh chứng và review/merge; không tự merge main.

Run đã kiểm tra qua GitHub API (job test và mysql đều completed/success):
https://github.com/khoizz247/store_ktpm/actions/runs/39525419290.

## Đối chiếu đề bài

| Yêu cầu | Bằng chứng trong dự án | Trạng thái |
|---|---|---|
| REST/JSON, POST/GET/DELETE | Auth, Product, Cart, Order controllers | Có code và test |
| OpenAPI/Swagger | OpenApiConfig, springdoc, /v3/api-docs | CI readiness đã pass |
| API → nghiệp vụ → dữ liệu | Controller/service/port/adapter, CurrentUserService bổ sung | Có; ArchUnit pass trên CI |
| Business không import web/DB | application/domain chỉ dùng Java và port/model | Review source; có bốn rule tự động |
| Repository + ORM/data mapper | JPA bốn bảng, JDBC giỏ/audit/search | Có |
| Login và GET/POST cần xác thực | JWT filter; GET /users/me, POST /orders | Có test quyền/JWT |
| Docker | Dockerfile, Compose MySQL8.4 + API, healthcheck | Build/start/readiness pass trên CI |
| Public GitHub/README | remote store_ktpm; README + API_SPEC + ARCHITECTURE | Có Git remote; không đổi visibility trong lượt này |
| Kiểm thử tải Kaggle CPU | Run 20261021T023505Z, metadata/CSV/HTML/log | Đã đối chiếu ZIP gốc đủ bốn pha |

Các lớp test thật có thể tồn tại dù chưa được chạy trong phiên này. Tồn tại test
không đồng nghĩa test đã pass, và H2 pass không chứng minh Flyway MySQL đúng.

## Tiến độ theo phần

- A: auth, JWT/filter, /me, đổi mật khẩu, handler lỗi; unit/integration test A5–A7
  đã lên main. Bổ sung profile member-a-test riêng và test handler database còn thiếu.
  API_SPEC/checkout-demo.http bổ sung tài liệu/quyền demo cho phần A8.
- B: sản phẩm, phân trang/tìm kiếm, soft delete, inventory API và test đã lên main.
  Sửa validation giá/tồn kho và version trong response cập nhật.
- C: API tạo/xem/tìm/hủy đơn và admin quản lý đơn, unit/API/concurrency test đã
  merge. Sửa isolation/cleanup test, validate item null, giới hạn tiền và actor audit.
- D: JDBC/transaction, giỏ/checkout, Docker/Flyway, script readiness và test
  checkout rollback/cạnh tranh đã có. Hoàn thiện D7 ArchUnit/Failsafe/CI và bộ
  chạy Kaggle. D7 đã có CI xanh ở 941ec24; phần tải đã đủ CSV/HTML/log và đối chiếu ZIP.

Đây là đánh giá theo code đã nhận, không phải kết luận mọi tình huống của từng
thành viên đều đúng. Không sửa lịch sử tác giả/ngày commit hoặc xóa công việc của nhóm.

## Lỗi đã sửa/bổ sung trong lượt rà soát

1. C7 test gắn cứng MySQL localhost:3306/root/mật khẩu mẫu: chuyển default test
   sang H2 RAM riêng; MySQL thật ở job Compose riêng, không dùng DB demo để cleanup.
2. Cleanup thiếu bảng FK: xóa audit → cart → order_items → orders → products → users
   và kiểm tra đúng URL H2 trước khi dọn. Không còn tạo inventory qua SQL test trùng.
3. Future của test cạnh tranh không timeout: giới hạn chờ, shutdown executor trong
   finally; assert chính xác một 201/một 409.
4. A API test chưa có profile riêng: thêm cấu hình H2/seed/JWT test cố định độc lập .env.
5. JUnit/Mockito bị khai báo trùng và ép phiên bản cũ: dùng spring-boot-starter-test
   và dependency management của Boot; không xóa các test của nhóm.
6. Order items=[null], thiếu quantity; PATCH stock={} hoặc null: chặn bằng validation,
   không NPE/không vô tình đưa kho về 0.
7. Giá vượt DECIMAL(15,2), tổng đơn vượt DB hoặc hoàn kho vượt INT: từ chối trước
   ghi dữ liệu; không tràn số hoặc để DB âm thầm làm tròn giá đầu vào.
8. Đăng ký với mật khẩu Unicode vượt giới hạn BCrypt: trả 400 trước lưu; chặn email
   quá dài so với VARCHAR(190).
9. Admin hủy đơn: truyền identity từ Authentication tới service, audit có actor;
   giữ no-op khi PATCH lại cùng trạng thái, không hoàn hai lần.
10. Product update trả version trước flush: saveAndFlush trước ánh xạ response.
11. /users/me: đi qua CurrentUserService thay vì controller đọc repository trực tiếp.
12. Bổ sung regression API/unit test, handler test cho lỗi DB/locking, API_SPEC và
    checkout-demo.http; cập nhật các mô tả tiến độ đã lỗi thời.
13. CI chạy verify (Surefire + Failsafe), ArchUnit và Python; job MySQL tạo database
    trong Compose project riêng, credential ngẫu nhiên, không phụ thuộc .env cá nhân.
14. Thêm runner/notebook Kaggle đúng schema V1/V2 và sáu bảng; converter không
    âm thầm bỏ pha thiếu CSV, có đối chiếu số liệu và quét mẫu lỗi trong api.log.

Chỉ xóa SQL test inventory trùng và dependency test dư. Không sửa/xóa Flyway V1/V2,
schema-h2.sql, API hợp lệ, .env, MySQL volume hoặc dữ liệu demo. File SQL test đã
xóa có thể khôi phục từ Git; schema thực vẫn do Flyway/H2 init quản lý.

## Kiểm tra thực tế trong lượt này

- **PASS**: 12 Python unit test scripts/verify_mysql (mock, không kết nối Docker).
- **PASS**: 2 Python unit test converter báo cáo tải (fixture, không có số liệu thật).
- **PASS**: parser cú pháp Java21 cho 84 source file; không thay kiểm tra kiểu/JUnit.
- **PASS**: cú pháp Python, code cell notebook, POM XML, cấu trúc workflow YAML và
  Python nhúng trong CI; git diff --check; không còn file conflict.
- **LOCAL BỊ CHẶN**: Maven dừng ở compiler với AccessDeniedException khi JDK
  đóng ZipFileSystem của JAR dependency. Không có tổng số test Java pass mới.
  Đã thử cache/bản làm việc riêng và compiler fork; không đổi ACL hoặc bỏ test để che lỗi.
- **LOCAL KHÔNG CHẠY DOCKER**: phiên công cụ hiện tại không tìm thấy Docker CLI; không
  suy ra Docker Desktop trên máy người dùng chưa cài/hỏng. Không đụng container đang dùng.
- **PASS CI 941ec24**: job test chạy Maven verify (Surefire/Failsafe + ArchUnit),
  12 test Python smoke script và 2 test report converter; tất cả bước thành công.
  Chưa tải XML report nên không ghi một tổng số JUnit suy đoán.
- **PASS MYSQL THẬT TRÊN CI 941ec24**: build/start Compose mới, API/MySQL healthy,
  schema sáu bảng và Flyway V1/V2, OpenAPI, bootstrap admin login và hai checkout
  cạnh tranh đều thành công. Job cleanup thành công, không dùng volume demo local.
- **PASS ĐỐI CHIẾU KAGGLE 941ec24**: CRC ZIP hợp lệ, 28 file nguồn, đúng metadata;
  đủ bốn pha 5/10/30/60 user và exit code 0. CSV snapshot có 13.943 request,
  0 thất bại, RPS 2,46 → 29,27; p95 82–87 ms. HTML/log cuối khớp nhau ở 13.958
  request, 0 thất bại; chênh 15 với CSV được ghi rõ, không sửa/trộn số liệu.
  Tám file failure/exception rỗng ngoài header, api.log không có ERROR/stack trace,
  profile mysql/Flyway V1/V2 thành công và shutdown bình thường. Bản công khai
  chỉ che mật khẩu phát triển ngẫu nhiên trong api.log; giữ nguyên các CSV/HTML.

## Tái kiểm tra và bàn giao

CI đã nghiệm thu phần build/test/Docker cho 941ec24. Các lệnh local bên dưới là
cách tái kiểm tra, không phải bằng chứng rằng CI chưa chạy. Dùng JDK21, không bật
profile MySQL cho test:

```powershell
.\mvnw.cmd clean verify
python -m unittest discover -s scripts -p 'test_*.py'
python -m unittest performance.test_report
```

Nếu Maven còn AccessDenied, giữ nguyên log lỗi; thử Maven Lifecycle verify trong
IntelliJ ở môi trường người dùng. Không xóa .m2/volume MySQL hoặc skip test để chốt.

Chạy lại Docker với .env cục bộ đã có, giữ cổng riêng (ví dụ API_HOST_PORT=18082):

```powershell
docker compose up --build -d --wait --wait-timeout 240
docker compose ps
python scripts/verify_mysql.py --base-url http://localhost:18082
```

Nếu muốn kiểm tra cạnh tranh, lệnh sau tạo customer/order/audit và giảm kho 2;
chỉ chạy trên DB demo lúc không có người khác sử dụng:

```powershell
python scripts/verify_mysql.py --base-url http://localhost:18082 --exercise-cart-concurrently
```

Đối chiếu Swagger/checkout-demo.http: admin login → tạo sản phẩm → customer register → đặt
hoặc checkout → stock giảm → hủy PENDING → stock hoàn đúng → hủy lại không hoàn thêm.
Hai job Actions đã xanh và Kaggle đã được đối chiếu cho
941ec24a8158a5bf072d823a129abff5415d0c30. Các thay đổi bàn giao sau benchmark chỉ
gồm tài liệu, minh chứng và thuộc tính giữ nguyên byte dữ liệu; không đổi mã chạy
ứng dụng hoặc workload được đo. Còn commit/push minh chứng, review nhánh,
CI của commit bàn giao và merge theo quy trình nhóm; không tự merge.

### Số liệu đã đối chiếu từ CSV gốc

| Pha | Users | Giây | Requests | Thất bại | RPS | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| smoke | 5 | 60 | 145 | 0 | 2,46 | 8 | 87 | 100 |
| baseline | 10 | 120 | 596 | 0 | 5,01 | 5 | 85 | 90 |
| main | 30 | 300 | 4437 | 0 | 14,82 | 3 | 82 | 88 |
| high | 60 | 300 | 8765 | 0 | 29,27 | 3 | 82 | 91 |

Tổng CSV 13.943; HTML/log cuối 13.958. RPS baseline của HTML cuối là 4,99 thay
vì 5,01 ở CSV snapshot; các giá trị còn lại được trình bày theo đúng nguồn tại
performance/REPORT.md. Không lấy số từ hai snapshot ghép thành một bảng giả.

Môi trường theo metadata/log: Kaggle CPU 4 luồng, RAM 31,35 GiB; Java21.0.12.1,
MySQL8.0.46, Locust2.32.1/Python3.13.15. API/MySQL/Locust cùng máy, mỗi user
có sản phẩm riêng và dữ liệu tăng giữa các pha. Trong workload đã đo chưa thấy
p95 xấu đi khi tăng user, nhưng không suy ra thông lượng tối đa/độ trễ production,
khả năng chịu hotspot SKU hoặc không có lỗi ngoài phạm vi bài đo.
ArchUnit đã có code; không có Testcontainers hay Postman/Newman trong bản nhóm
này. Hai công cụ đó không phải yêu cầu tối thiểu của đề bài đã cung cấp; nếu nhóm
đã cam kết chúng trong kế hoạch riêng thì không đánh dấu mục đó hoàn thành.

## Giới hạn baseline còn lại — vấn đề và ảnh hưởng

| Vấn đề | Ảnh hưởng có thể có |
|---|---|
| Danh sách đơn tìm ID rồi đọc từng đơn/items | Nhiều query, độ trễ tăng khi dữ liệu lớn |
| Khóa sản phẩm/giỏ trong transaction | Request cùng tài nguyên có thể phải chờ hoặc nhận 409 |
| Cart PUT kiểm tra tồn tại rồi insert/update | Hai request thêm cùng dòng mới có thể xung đột UNIQUE |
| Inventory history chưa phân trang | Response/tài nguyên tăng theo số bản ghi |
| JWT chưa thu hồi/refresh; role claim có thể cũ | Token cũ còn dùng tới hết hạn; đổi mật khẩu chưa thu hồi token cũ |
| Chưa có rate limit đăng nhập hoặc idempotency key tạo đơn | Tăng rủi ro spam/brute force; client retry có thể tạo đơn khác |
| Tìm kiếm dạng contains, count và đọc page ở các truy vấn riêng | Chậm khi dữ liệu lớn; tổng số và nội dung có thể lệch khi dữ liệu thay đổi đồng thời |
| H2 và MySQL khác dialect/locking/ràng buộc | Kết quả H2 không bao phủ toàn bộ hành vi MySQL |
| API/MySQL/Locust cùng CPU Kaggle, mỗi user dùng sản phẩm riêng | Không đại diện hạ tầng production hoặc hotspot SKU |

Các giới hạn trên được nêu để bàn giao baseline Pha 2, không mở rộng thêm nghiệp
vụ đa cửa hàng hoặc tuyên bố hệ thống đã sẵn sàng production.
