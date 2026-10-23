# My Finance

Backend Spring Boot 4, Java 17 và Maven Wrapper 3.9.12; sử dụng MySQL 8 và MongoDB 7.

## Chạy local

Cần JDK 17 và Docker Compose. Từ thư mục gốc:

```bash
# .env chứa cấu hình database local; không dùng credentials này cho production.
set -a
. ./.env
set +a
docker compose up -d --wait mysql mongodb

# Khóa ngẫu nhiên tối thiểu 32 byte, mã hóa Base64. Không commit hoặc in khóa ra log.
export FINANCE_JWT_SECRET="$(openssl rand -base64 32)"
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3307/$MYSQL_DATABASE"
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD="$MYSQL_ROOT_PASSWORD"
export SPRING_MONGODB_URI="mongodb://localhost:27017/$MONGO_DB"
cd backend
sh mvnw verify
sh mvnw spring-boot:run
```

Trong cloud environment đã thiết lập, sử dụng helper giữ ngoài repository:

```bash
# Vẫn cần export FINANCE_JWT_SECRET trước khi chạy ứng dụng.
python /workspace/.setup/my-finance/run.py verify
python /workspace/.setup/my-finance/run.py start
```

`FINANCE_JWT_SECRET` bắt buộc khi chạy ứng dụng: thiếu khóa hoặc khóa quá ngắn sẽ khiến ứng dụng không khởi động. Production cần khóa riêng, ổn định, được cung cấp qua secret manager; dùng HTTPS. Đổi khóa làm mất hiệu lực các token đã cấp. `FINANCE_JWT_EXPIRATION_MS` mặc định là 3600000 (1 giờ). Các test dùng khóa fixture riêng, không dùng khóa production.

Dockerfile hiện chưa copy JAR vào final image; các lệnh trên chạy backend bằng Maven/Java và dùng Compose cho database.

## Xác thực và quyền truy cập

Chỉ `POST /api/auth/register` và `POST /api/auth/login` được truy cập không cần xác thực. Các endpoint khác yêu cầu `Authorization: Bearer <token>`. API dùng JWT stateless, không tạo session hoặc dùng cookie xác thực; CSRF được tắt cho cơ chế Bearer header này.

Đăng ký:

```json
{"username":"alice","password":"a-long-password","email":"alice@example.com","fullName":"Alice"}
```

Username dài 3–64 ký tự; password dài 8–72 ký tự và không vượt 72 byte UTF-8 (giới hạn BCrypt); email bắt buộc và hợp lệ. Server tự gán `ROLE_USER`, băm password bằng BCrypt và không trả password/hash trong JSON.

Đăng nhập với `{"username":"alice","password":"a-long-password"}` nhận `{"userId":1,"username":"alice","token":"...","message":"Login successful"}`. Token sai, hết hạn hoặc tài khoản không tồn tại trả HTTP 401. Thông tin đăng nhập sai trả thông báo chung. Tài khoản trùng trả HTTP 409; dữ liệu đầu vào sai trả HTTP 400.

Mỗi tài khoản chỉ được thao tác dữ liệu của chính mình. Các đường dẫn chứa `userId` giữ nguyên, nhưng yêu cầu ID trùng tài khoản được xác thực; ID tài khoản khác trả HTTP 403. Server lấy chủ sở hữu từ JWT, không tin `userId` header hoặc trường `user`/`id` trong payload.

- `POST /api/categories`: `{"name":"Food","type":"EXPENSE"}`.
- `GET /api/categories/user/{userId}`: danh mục của tài khoản hiện tại.
- `POST /api/transactions`: `{"amount":100,"type":"EXPENSE","categoryId":1,"description":"Lunch","transactionDate":"2026-10-13"}`. Category phải thuộc tài khoản hiện tại. Payload mới dùng `categoryId` thay cho object `category`; không cần header `userId`.
- `GET /api/transactions/user/{userId}`: giao dịch của tài khoản hiện tại.
- `GET /api/transactions/user/{userId}/range?start=2026-10-01&end=2026-10-31`: lọc giao dịch theo ngày.

Chưa có refresh token, thu hồi token riêng lẻ hoặc rate limiting. Logout ở client bằng cách xóa token; token đã cấp vẫn có hiệu lực tới khi hết hạn hoặc khóa ký thay đổi.

## Kiểm thử

`sh mvnw verify` chạy kiểm thử context và integration với MySQL/MongoDB đang hoạt động. Integration test kiểm tra đăng ký/đăng nhập, băm password, validation, JWT sai/hết hạn/giả mạo, dữ liệu khác tài khoản, và ghi giao dịch/log. Test tạo dữ liệu với username ngẫu nhiên và xóa dữ liệu đó sau khi chạy; nên dùng database phát triển riêng.

## Giao diện React

Ứng dụng có frontend tiếng Việt tại `frontend/`. Chạy backend ở cổng 8080, rồi mở terminal khác:

```bash
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:5173` để đăng ký/đăng nhập, tạo danh mục, ghi thu chi và xem tổng quan. Frontend tự quản lý Bearer token, không cần Postman. Cần Node.js 22.12+ hoặc 24 LTS. Xem [hướng dẫn frontend](frontend/README.md) để cấu hình proxy, build và chạy kiểm thử.
