# Sổ nhỏ — React frontend

Giao diện tiếng Việt để dùng My Finance trên trình duyệt: đăng ký/đăng nhập, quản lý danh mục, ghi thu chi, tổng quan dòng tiền, tìm kiếm và lọc giao dịch. Dữ liệu lấy từ Spring Boot, không có dữ liệu mẫu trong ứng dụng.

## Chạy trên Windows / IntelliJ

1. Cài Node.js **22.12+** hoặc **24 LTS**. Backend dùng JDK 17.
2. Mở backend trong IntelliJ, cấu hình MySQL/MongoDB và `FINANCE_JWT_SECRET` theo README ở thư mục gốc, sau đó chạy `PersonalFinanceApplication` ở cổng **8080**.
3. Mở terminal tại thư mục repository và chạy:

```powershell
cd frontend
npm ci
npm run dev
```

4. Mở `http://localhost:5173` trong trình duyệt. Đăng ký tài khoản mới hoặc đăng nhập bằng tài khoản đã có. Frontend tự gửi JWT và lấy ID tài khoản; không cần Postman.

Vite chuyển tiếp `/api` tới `http://127.0.0.1:8080`, nên không cần tắt bảo vệ trình duyệt hoặc mở rộng CORS của backend. Khi backend ở địa chỉ khác, copy `.env.example` thành `.env.local` và đổi `API_PROXY_TARGET`, rồi khởi động lại Vite.

**Không đưa `FINANCE_JWT_SECRET` hoặc bất kỳ khóa bí mật nào vào frontend hay biến `VITE_*`.** Khóa ký chỉ thuộc backend.

## Cách dùng

- **Danh mục**: thêm tên và loại Thu nhập/Chi tiêu trước khi tạo giao dịch.
- **Thêm giao dịch**: chọn loại, số tiền VND nguyên dương, danh mục cùng loại và ngày; ghi chú không bắt buộc.
- **Tổng quan**: tổng thu, tổng chi và chênh lệch trong khoảng ngày được chọn; đây không phải số dư tài khoản ngân hàng. Biểu đồ chỉ tổng hợp dữ liệu đã lưu.
- **Giao dịch**: lọc khoảng ngày, loại thu/chi, tìm theo ghi chú hoặc tên danh mục. Bộ lọc xử lý trong frontend sau khi tải dữ liệu của tài khoản.
- Đăng xuất khi xong. Token chỉ giữ trong `sessionStorage` của tab hiện tại; refresh trang vẫn đăng nhập, đóng tab kết thúc việc lưu phiên. HTTP 401 hoặc token hết hạn sẽ đưa về đăng nhập. Frontend không thực hiện xác minh chữ ký JWT; backend luôn kiểm tra quyền thực tế.

Backend hiện hỗ trợ tạo và xem; giao diện không cung cấp sửa/xóa vì chưa có API tương ứng.

## Build

```powershell
npm run build
npm run preview
```

Bản build ở `dist/`. Vite preview dùng proxy `/api` như chế độ dev để kiểm tra local. Khi triển khai production, phục vụ `dist/` bằng web server và cấu hình reverse proxy `/api` tới Spring Boot; không dùng Vite dev server làm production server. API và frontend cần HTTPS khi public.

## Kiểm thử trình duyệt

```powershell
npx playwright install chromium
npm test
```

Suite mặc định dùng API giả lập ở mức HTTP để kiểm tra payload/Authorization, đăng ký và tự đăng nhập, tạo danh mục/giao dịch, lọc dữ liệu, giữ phiên sau reload, logout, HTTP 401/503 và bố cục mobile. Không cần backend cho suite này.

Kiểm tra xuyên suốt với **backend và database thật đang chạy**:

```powershell
npm run test:live
```

Live test tạo một tài khoản `ui_<timestamp>`, một danh mục và một giao dịch trong database phát triển. Không chạy trên production. Chưa có API xóa nên dữ liệu kiểm thử được giữ lại. Có thể chọn Chromium cài sẵn qua `CHROMIUM_PATH`; trên cloud, cấu hình tự dùng `/usr/bin/chromium` khi có.


## Module chứng khoán

Chọn **Chứng khoán** → ghi nhận Mua/Bán → nhập giá tham chiếu cho mã đã mua. Giao diện hiển thị số cổ phiếu, giá vốn bình quân, giá trị, lãi/lỗ đã chốt/chưa chốt và lịch sử. Đơn vị giá là **VND cho một cổ phiếu**, không phải nghìn đồng. Giá tham chiếu là giá nhập tay, có thời điểm cập nhật; không có kết nối sàn hoặc đặt lệnh thật.

Chưa hỗ trợ sửa/xóa, cổ tức và chia tách. Phí mua/bán được tính vào lãi/lỗ; các khoản thuế khác chỉ được tính nếu bạn gộp vào phí nhập. Giao dịch đầu tư không tự ghi vào sổ thu chi cá nhân. Tài khoản không thể xem hoặc sửa sổ đầu tư của người khác.

`npm test` bao gồm kiểm thử mua cổ phiếu, cập nhật giá, lịch sử, phản hồi bán vượt số lượng và bố cục mobile. `npm run test:live` kiểm tra thêm mua/cập nhật giá/bán với backend thật; test tạo tài khoản và dữ liệu đầu tư riêng trong database phát triển.
