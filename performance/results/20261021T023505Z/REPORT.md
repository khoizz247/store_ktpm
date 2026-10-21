# Báo cáo kiểm thử tải Single Store Team

- Thời điểm UTC: 2026-10-21T02:37:08.258475+00:00
- Git commit: `941ec24a8158a5bf072d823a129abff5415d0c30`
- Nơi chạy: Kaggle CPU Notebook
- CPU: 4 luồng; RAM: 31.35 GiB
- Java: openjdk version "21.0.12.1" 2026-08-18
- MySQL: 8.0.46-0ubuntu0.24.04.4
- Locust: locust 2.32.1 from /usr/local/lib/python3.13/dist-packages/locust (Python 3.13.15)
- API và MySQL chạy trên cùng phiên CPU; Locust gọi qua localhost.
- Trọng số tác vụ mục tiêu: 60% danh sách sản phẩm, 15% chi tiết, 10% đăng nhập, 10% đơn của tôi, 5% tạo đơn.
- Mỗi người dùng ảo có một sản phẩm tồn kho lớn riêng; request chuẩn bị tài khoản/sản phẩm không tính vào số liệu Locust.

| Pha | Users | Giây | Requests | Lỗi | Lỗi % | RPS | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| smoke | 5 | 60 | 145 | 0 | 0.0 | 2.46 | 8.0 | 87.0 | 100.0 |
| baseline | 10 | 120 | 596 | 0 | 0.0 | 5.01 | 5.0 | 85.0 | 90.0 |
| main | 30 | 300 | 4437 | 0 | 0.0 | 14.82 | 3.0 | 82.0 | 88.0 |
| high | 60 | 300 | 8765 | 0 | 0.0 | 29.27 | 3.0 | 82.0 | 91.0 |

## Đối chiếu tự động

- Tổng request trong các pha có CSV: 13943; tổng request thất bại: 0.
- RPS từ pha đầu đến pha cuối có số liệu: 2.46 → 29.27; p95 trong khoảng 82.0–87.0 ms.
- Số liệu chỉ mô tả các pha có trong bảng; một pha smoke không chứng minh toàn bộ bốn mức tải.
- Các pha chạy tuần tự trên cùng database, dữ liệu người dùng/đơn tăng dần; đây là giới hạn của phép so sánh giữa các pha.
- Kết quả là baseline Pha 1, không phải cam kết hiệu năng production.

## Tệp minh chứng

`metadata.json`, `summary.csv`, `*_stats.csv`, `*_failures.csv`, `*_stats_history.csv`, `*_report.html`, `api.log`.

- Quét api.log: 0 dòng có mẫu ERROR/Exception. Đây là bộ lọc văn bản, không thay thế đọc log hoặc chứng minh tuyệt đối không có lỗi.
- Đối chiếu thêm *_failures.csv, *_exceptions.csv, HTML và commit trước khi nghiệm thu.
