# Kaggle CPU — baseline của repository nhóm

Chưa có số liệu đo mới trong thư mục này. Không copy CSV/report của sales-service
hay single-store-service rồi đổi tên: kết quả phải ghi đúng commit store_ktpm.
Bộ runner/workload/report được tái sử dụng và điều chỉnh từ performance của
sales-service: database single_store_team, sáu bảng, Flyway V1/V2, JAR single-store,
API sản phẩm/đơn và admin bootstrap của repository nhóm.

## Thực hiện

1. Chạy Maven verify và CI trước. Commit/push hoặc merge phiên bản cần đo, kiểm
   tra commit đó có performance/kaggle_run.py. Không benchmark code chưa commit.
2. Tạo Kaggle Python Notebook, Accelerator=None (CPU), bật Internet. Import
   `kaggle_load_test.ipynb`. Nhập SHA commit của phiên bản cần đo ở cell đầu.
3. Chạy clone/checkout, rồi runner. Để chạy đủ bốn pha, không đặt LOAD_SMOKE_ONLY.
   Nếu chỉ thử khả thi, đặt LOAD_SMOKE_ONLY=1; sau đó dùng session Kaggle mới cho
   lượt đo đầy đủ. Không chạy lại runner trên schema đã có dữ liệu.
4. Cài MySQL 8, Java 21 và Locust có thể mất vài phút. Nếu apt/pip/Internet bị
   chặn, giữ log lỗi; không đổi sang H2 rồi tuyên bố đã đo MySQL.
5. Tải ZIP ở `/kaggle/working/single_store_load_results.zip` từ Output.
   Review metadata, REPORT.md, summary.csv, *_stats.csv, *_failures.csv,
   *_exceptions.csv, *_stats_history.csv, *_report.html và api.log.

Runner chỉ cho chạy Linux/root trong /kaggle/working; MySQL/API nằm trong chính
session đó, không kết nối MySQL trên máy cá nhân. Không cần Docker trong Kaggle.
MySQL dùng schema mới; Flyway tạo V1/V2 và Hibernate validate. Credential ngẫu
nhiên chỉ dùng trong phiên; không xuất fixture có JWT/password vào minh chứng.
Runner build JAR với test skip để không trộn thời gian JUnit vào phép đo;
đây KHÔNG phải bằng chứng các test Java đã pass.

## Kịch bản

| Pha | Người dùng ảo | Thời gian |
|---|---:|---:|
| smoke | 5 | 60 giây |
| baseline | 10 | 120 giây |
| main | 30 | 300 giây |
| high | 60 | 300 giây, không tiếp tục khi main lỗi |

Mỗi user đợi ngẫu nhiên 1–3 giây giữa tác vụ, tăng tải 5 user/giây.
Trọng số: 60% danh sách sản phẩm, 15% detail, 10% login, 10% đơn của tôi, 5% tạo đơn.
Chuẩn bị tài khoản/sản phẩm riêng trước từng pha, không tính request chuẩn bị.
Không dùng tải ghi liên tục không nghỉ để suy ra kết quả tương đương kịch bản này.

## Giới hạn và tiêu chí nghiệm thu

- API, MySQL và Locust chia sẻ CPU/RAM Kaggle, gọi localhost, không đo mạng WAN.
- Mỗi user có sản phẩm riêng: đây là workload tổng hợp, không đo hotspot nhiều
  người tranh cùng SKU; kiểm thử cạnh tranh riêng không thay cho bài tải này.
- Dữ liệu tăng giữa các pha; so sánh RPS/p95/p99 chịu ảnh hưởng warm-up và DB lớn dần.
- Chưa có SLO chính thức; không suy ra đáp ứng production chỉ từ 0 failed request.
- Báo cáo sinh kết luận số học từ CSV và quét mẫu log. Đọc log/HTML thực tế trước
  nghiệm thu; 0 dòng ERROR theo regex không chứng minh tuyệt đối không có lỗi.
- Muốn tuyên bố chạy đủ bốn pha, phải có đủ bốn dòng và log/CSV tương ứng.

## Kiểm tra offline

```powershell
python -m unittest performance.test_report
```

Đây chỉ là test converter với fixture, không phát sinh số liệu hiệu năng thật.
Locust CSV: https://docs.locust.io/en/2.32.1/retrieving-stats.html.
Kaggle notebook: https://www.kaggle.com/docs/notebooks.
