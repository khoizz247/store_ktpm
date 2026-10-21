# D7 — kiểm tra kiến trúc và MySQL trong CI

## Maven lifecycle và test kiến trúc

`ArchitectureTest` dùng ArchUnit 1.5.0 (test scope), import bytecode production,
không import test fixtures. Bốn rule JUnit:

1. `application..` chỉ phụ thuộc Java, application và domain; không web/DB/Spring.
2. `domain..` chỉ phụ thuộc Java/domain.
3. `api..` không import infrastructure hoặc config của dự án.
4. `infrastructure..` không import api hoặc config của dự án.

Config là composition root, được nối controller/error mapping, service và
adapter nên không áp cùng rule với business. Các package `application.service`
và `application.services` hiện có đều được kiểm tra, không chỉ một cách viết.
Rule API cho phép dùng application ports; nó KHÔNG chứng minh mọi request đều
đi qua một service hoặc tất cả logic nghiệp vụ đều đặt đúng chỗ. Vẫn cần review.
Không dùng baseline/ignore để âm thầm bỏ qua vi phạm mới.

ArchUnit phân tích bytecode/dependency, không đo hiệu năng hoặc test quyền HTTP.
Tham khảo: https://www.archunit.org/userguide/html/000_Index.html.

Pom thêm Maven Failsafe 3.5.3, bind `integration-test` và `verify`:

| Lệnh | Chạy gì? |
|---|---|
| `.\mvnw.cmd test` | Surefire chọn các lớp theo quy ước Test, gồm ArchitectureTest |
| `.\mvnw.cmd verify` | Surefire ở trên + Failsafe chọn các lớp theo quy ước IT |
| `.\mvnw.cmd -Dtest=ArchitectureTest test` | Riêng bốn rule kiến trúc |
| `.\mvnw.cmd -Dit.test=ProductAuditAndSeedIT verify` | Unit/architecture và IT được chỉ định |

Không chỉ chạy `integration-test` rồi bỏ `verify`: Failsafe tổng kết fail ở verify.
Không cần thêm `-Dtest=*Test,*IT` nữa; lệnh cũ có thể khiến IT chạy ở cả hai plugin.
Các integration test đang mang tên `*Test` vẫn chạy bằng Surefire, không đổi tên
file của thành viên khác. Tên file quyết định discovery, không quyết định test
có thực sự dùng database hay không.

`ProductAuditAndSeedIT` của B giờ được lifecycle verify chọn tự động. Đây là test
H2, không phải Testcontainers/MySQL. C7 SingleStoreApiTest chạy HTTP thật qua
RANDOM_PORT trên H2 riêng, có hai khách tranh sản phẩm cuối cùng. Profile test
không gắn MySQL root/3306 vào mặc định verify; cleanup kiểm tra đúng H2 trước khi xóa.
Tham khảo: https://maven.apache.org/surefire/maven-failsafe-plugin/.

## Workflow GitHub Actions

Workflow `.github/workflows/ci.yml` chạy khi push và pull request:

### Job test

- Ubuntu runner, Temurin Java 21, Maven Wrapper verify.
- Chạy cả Surefire và Failsafe, không skip IT.
- Chạy Python unittest cho scripts (mock, không dùng Docker).
- Upload surefire-reports và failsafe-reports kể cả khi fail để review.

### Job mysql, sau khi job test thành công

- Project Compose `single-store-ci-<run_id>-<run_attempt>`; volume/network mới
  theo project. Không dùng tài nguyên `single-store-team` trên máy demo.
- Database vẫn tên `single_store_team`, nhưng ở MySQL riêng của runner.
- Cổng CI: API 18091, MySQL 13309; không sửa cổng local trong .env của nhóm.
- Tạo password DB/root/bootstrap và JWT ngẫu nhiên cho mỗi job; mask trước khi
  ghi GITHUB_ENV. Không cần tạo secret dùng chung hoặc commit .env CI.
- Build/start Compose, chờ MySQL và API healthy (timeout 240 giây).
- Script readiness kiểm tra đúng database, sáu bảng, Flyway V1/V2 thành công,
  không có migration lỗi, health và OpenAPI. API mysql profile chạy Hibernate
  validate; không dùng H2/ddl-auto update để thay thế phép thử deployment.
- Đăng nhập admin bootstrap bằng API thật, không in password/JWT.
- Hai checkout cạnh tranh trên MySQL: một 201/một 409, một order/item/SALE,
  stock giảm đúng 2 và giỏ rỗng. Dữ liệu test nằm ở database runner.
- Nếu fail, in ps và log API/MySQL đã mask; không in compose config nội suy.
- Luôn cleanup với đúng project name và kiểm tra prefix CI trước khi xóa volume.
  Lệnh cleanup workflow chỉ dành cho tài nguyên tạm của job; KHÔNG chạy trên
  project local `single-store-team`.

Project name CLI/env được Compose dùng để cô lập tài nguyên:
https://docs.docker.com/compose/how-tos/project-name/.
Masking secret của Actions:
https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets.

Workflow kiểm tra cả database mới và API thực nhưng không phải Kaggle load test,
không thay C7 test hai customer đặt đơn trực tiếp hoặc mọi trường hợp concurrency.
Không có Testcontainers trong D7; không báo một test MySQL đã pass chỉ vì job bị skip.

## Chạy và nghiệm thu

Local, từ thư mục repository:

```powershell
.\mvnw.cmd verify
python -m unittest discover -s scripts -p 'test_*.py'
```

Nếu cần smoke MySQL trên máy demo, mở Docker Desktop rồi:

```powershell
docker compose up --build -d --wait --wait-timeout 240
python scripts/verify_mysql.py --base-url http://localhost:18082
python scripts/verify_mysql.py --base-url http://localhost:18082 --exercise-cart-concurrently
```

Thay 18082 theo API_HOST_PORT thực tế. Flag cuối giữ customer/order/audit và
giảm kho 2; không tự hủy/xóa. Chạy trên DB demo không có người khác mua đồng thời.

Cuối cùng push nhánh và xem Actions: cả job test lẫn mysql phải xanh cho đúng
commit trước khi nghiệm thu CI. YAML hợp lệ hoặc Java đã xanh ở commit trước
không chứng minh workflow mới đã chạy thành công. Nếu fail, đọc job/step lỗi và
download reports; không bỏ check hoặc tăng timeout vô hạn để che nguyên nhân.
