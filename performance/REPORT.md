# Báo cáo nghiệm thu tải Single Store Team — Pha 1

## Phiên bản và kết luận

- Commit ứng dụng đã đo: `941ec24a8158a5bf072d823a129abff5415d0c30`.
- Run Kaggle: `20261021T023505Z`, ngày 21/10/2026; đủ bốn pha 5/10/30/60 user.
- Đã đọc ZIP gốc, kiểm tra CRC, metadata, từng CSV, HTML, log Locust và api.log.
- Các pha đều exit code 0; tám file failures/exceptions không có dòng dữ liệu lỗi.
- CSV snapshot: **13.943 request, 0 thất bại**; thống kê cuối HTML/log:
  **13.958 request, 0 thất bại**. Hai tập số liệu được phân biệt ở phần đối chiếu.
- Có đủ minh chứng kiểm thử tải cho baseline Pha 1. Kết hợp CI xanh của cùng
  commit, bản một cửa hàng đáp ứng các yêu cầu Pha 1 đã cung cấp.

Kết luận không đồng nghĩa tối ưu hoặc sẵn sàng production. Phần nộp/bàn giao còn
cần push minh chứng, review nhánh và merge theo quy trình nhóm; báo cáo không tự merge.

## Môi trường và phương pháp

Kaggle CPU Notebook, 4 CPU threads, RAM 31,35 GiB; Java21.0.12.1,
MySQL8.0.46 (không phải H2), Locust2.32.1/Python3.13.15.
API/MySQL/Locust cùng một phiên CPU, HTTP qua localhost:18080.
Docker CI dùng MySQL8.4 riêng; đây là hai môi trường kiểm chứng khác nhau.

Load runner kiểm tra schema mới và Flyway V1/V2, Hibernate validate; api.log xác
nhận profile mysql, hai migration được áp dụng thành công và shutdown bình thường.
Chuẩn bị account/product trước từng pha không tính vào thống kê Locust.
Mỗi user có sản phẩm riêng tồn kho lớn, đợi ngẫu nhiên 1–3 giây giữa các tác vụ,
tăng tải 5 user/giây. Các pha chạy tuần tự và dùng chung database.

Trọng số mục tiêu: 60% danh sách sản phẩm, 15% chi tiết, 10% đăng nhập,
10% đơn của tôi, 5% tạo đơn. Không phải 60 HTTP request đồng thời liên tục.

## Kết quả CSV snapshot — bảng chính

Nguồn nguyên trạng: [summary.csv](results/20261021T023505Z/summary.csv) và dòng
Aggregated của từng `*_stats.csv`. Không cộng lại dòng Aggregated với các endpoint.

| Pha | Users | Giây cấu hình | Requests | Thất bại | RPS | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| smoke | 5 | 60 | 145 | 0 | 2,46 | 8 | 87 | 100 |
| baseline | 10 | 120 | 596 | 0 | 5,01 | 5 | 85 | 90 |
| main | 30 | 300 | 4437 | 0 | 14,82 | 3 | 82 | 88 |
| high | 60 | 300 | 8765 | 0 | 29,27 | 3 | 82 | 91 |
| Tổng | — | — | 13943 | 0 | — | — | — | — |

Không cộng RPS, không lấy trung bình đơn giản p95/p99 của các pha để tạo một
percentile toàn bài đo. RPS/p50/p95/p99 ở đây lấy đúng snapshot từng pha.

### Đối chiếu theo endpoint — cộng bốn CSV snapshot

| Endpoint | Requests | Thất bại |
|---|---:|---:|
| GET /api/products | 8318 | 0 |
| GET /api/products/{id} | 2105 | 0 |
| POST /api/auth/login | 1420 | 0 |
| GET /api/orders/me | 1392 | 0 |
| POST /api/orders | 708 | 0 |
| Tổng | 13943 | 0 |

Đây là số request API, không phải kết quả kiểm kê trực tiếp số order/audit trong
database sau bài tải. Tính đúng dữ liệu được kiểm chứng riêng bằng integration
test và smoke MySQL trên CI.

## Đối chiếu CSV với HTML/log cuối chạy

| Pha | CSV snapshot | HTML/log cuối | Chênh request | RPS CSV | RPS HTML cuối |
|---|---:|---:|---:|---:|---:|
| smoke | 145 | 147 | 2 | 2,46 | 2,46 |
| baseline | 596 | 598 | 2 | 5,01 | 4,99 |
| main | 4437 | 4441 | 4 | 14,82 | 14,82 |
| high | 8765 | 8772 | 7 | 29,27 | 29,27 |
| Tổng | 13943 | 13958 | 15 | — | — |

HTML và log cuối khớp nhau ở cả bốn pha; p95/p99 tổng hợp cũng khớp CSV. History
có User Count đạt đủ 5/10/30/60, cumulative failure luôn 0 và request tăng đơn điệu.
Các mốc HTML UTC: smoke 02:37:13–02:38:13, baseline 02:38:17–02:40:17,
main 02:40:25–02:45:24, high 02:45:38–02:50:38 (xấp xỉ thời lượng cấu hình).

Locust ghi CSV theo chu kỳ, không phải mọi định dạng là một snapshot đồng thời.
Chênh lệch nhỏ ở cuối chạy phù hợp với việc writer CSV dừng trước một số request
cuối; archive không có request-level trace để chứng minh thời điểm từng request.
Vì vậy giữ nguyên cả hai số và không thay CSV bằng số HTML/log.
Tham khảo cơ chế ghi định kỳ: [Locust 2.32.1 CSV statistics](https://docs.locust.io/en/2.32.1/retrieving-stats.html).

## Kiểm tra lỗi và bảo toàn minh chứng

- CRC ZIP hợp lệ; có 28 file dữ liệu trong một run, không có đường dẫn thoát thư mục.
- metadata ghi đúng commit; users/duration/exit code của cả bốn pha đúng cấu hình.
- Summary khớp CSV từng pha; tổng endpoint bằng Aggregated, failure đều 0.
- Tám CSV failures/exceptions chỉ có header; HTML không có failure/exception entries.
- Bốn log Locust kết thúc với exit code 0, không thấy ERROR/Traceback/Exception.
- Đọc toàn bộ api.log: MySQL/Flyway khởi động thành công, API phục vụ và shutdown
  bình thường; không thấy ERROR/stack trace trong log được cung cấp.
- api.log có WARN về mật khẩu phát triển ngẫu nhiên do Spring tự sinh. Đây
  không phải lỗi request trong bài đo; giá trị đã được che trong bản công khai.
  Auth thực tế của dự án dùng JWT; không đưa giá trị mật khẩu đó vào báo cáo/PR.

ZIP gốc được giữ nguyên trên máy người dùng. SHA256:
`e76a2b7c4c607fc791a155e19a01ed38c6f7e9b4655aa835a79651bc6a092fdb`.

Thư mục [results/20261021T023505Z](results/20261021T023505Z) chứa bản công khai.
`manifest.json` ghi SHA256 nguồn và SHA256 bản công khai của 28 file:
27 file giữ nguyên byte; chỉ api.log che giá trị sau `Using generated security password:`.
Không sửa CSV, metadata, HTML, percentile hoặc request count.
`validation.json` lưu các phép đối chiếu đã thực hiện. REPORT.md trong run là
báo cáo tự sinh nguyên gốc; báo cáo nghiệm thu có nhận xét cuối là tài liệu này.

## Nhận xét và giới hạn

Trong kịch bản đã đo, RPS tăng từ 2,46 lên 29,27 khi tăng 5 lên 60 user,
p95 tổng hợp 82–87 ms và không ghi nhận failed request. Chưa thấy độ trễ p95
tăng theo tải trong các pha này. Không kết luận được hệ thống tối đa bao nhiêu RPS:
think time 1–3 giây giới hạn tốc độ phát request của workload.

Ở pha high, CSV cho p95: danh sách sản phẩm 3 ms, chi tiết 4 ms, đơn của tôi
10 ms, tạo đơn 25 ms, đăng nhập 95 ms. Đây là số liệu quan sát, không phải bằng
chứng nhân quả cho một bottleneck cụ thể.

Giới hạn: một lượt đo, không có khoảng tin cậy; warm-up và dữ liệu tăng giữa các
pha; cùng CPU/RAM cho load generator/API/DB; localhost không có WAN/TLS; phần lớn
request là đọc và mỗi user ghi vào sản phẩm riêng. Chưa đo hotspot SKU, soak test,
điểm bão hòa, memory/CPU per-process hoặc khả năng production. Các giới hạn này
được bàn giao làm baseline cho Pha 2, không che đi hoặc tối ưu trước khi so sánh.

## Bằng chứng build/test cùng phiên bản

[GitHub Actions của commit 941ec24](https://github.com/khoizz247/store_ktpm/actions/runs/39525419290)
đã thành công cả job test và mysql: Maven verify/ArchUnit, Python tests,
Docker/MySQL mới, Flyway readiness, bootstrap admin login và competing checkout.
Kaggle build JAR có skip JUnit để đo tải; không dùng BUILD SUCCESS của notebook
để thay thế kết quả test Java từ CI.
