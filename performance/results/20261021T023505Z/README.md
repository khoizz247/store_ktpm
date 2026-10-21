# Minh chứng Kaggle 21/10/2026

Ứng dụng được đo ở commit `941ec24a8158a5bf072d823a129abff5415d0c30`.
Nguồn: single_store_load_results.zip do người dùng tải từ Kaggle và cung cấp.
SHA256 ZIP gốc: `e76a2b7c4c607fc791a155e19a01ed38c6f7e9b4655aa835a79651bc6a092fdb`.

Có 28 file nguồn. 27 file giữ nguyên byte; api.log chỉ che mật khẩu phát triển
ngẫu nhiên do Spring in ra. ZIP gốc không bị thay đổi và không được đưa công khai.
manifest.json chứa hash nguồn và hash công khai; validation.json là kết quả đối chiếu.
Các file metadata/CSV/HTML không bị sửa hoặc làm tròn lại.

Đọc [báo cáo nghiệm thu](../../REPORT.md) để biết kết luận và giới hạn, đặc biệt:
CSV snapshot tổng 13.943 request; HTML/log cuối 13.958 request; cả hai không có
failure. Không trộn số liệu của hai snapshot. REPORT.md trong thư mục run này
được giữ nguyên bản tự sinh, không phải báo cáo nghiệm thu đã bổ sung nhận xét cuối.

HTML là báo cáo tĩnh Locust, không phải API/server cần khởi động. CSV/JSON/log là
dữ liệu minh chứng, không phải script cần thực thi. Không upload .env, fixture
password/token hoặc database cá nhân vào thư mục này.
