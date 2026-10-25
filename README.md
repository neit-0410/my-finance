# My Finance

Backend Spring Boot 4, Java 17 và Maven Wrapper 3.9.12; sử dụng MySQL 8 và MongoDB 7.

## Chạy bằng Docker (khuyên dùng)

Toàn bộ React, Spring Boot, MySQL, MongoDB và Redis có thể chạy bằng Docker Desktop, không cần IDE hoặc npm trên máy. Xem [hướng dẫn Docker cho Windows](docker/README.md) để tạo `.env.docker` và khóa JWT lần đầu. Sau đó chạy:

```powershell
docker compose --env-file .env.docker up -d --build --wait
```

Mở **http://localhost:8088**. Cấu hình/khóa nằm ngoài image và dữ liệu được giữ trong Docker volumes.

## Chạy local bằng IDE

Cần JDK 17 và Docker Compose. Từ thư mục gốc:

```bash
# .env chứa cấu hình database local; không dùng credentials này cho production.
set -a
. ./.env
set +a
docker compose -f docker-compose.yaml -f docker-compose.dev.yaml up -d --wait mysql mongodb redis

# Khóa ngẫu nhiên tối thiểu 32 byte, mã hóa Base64. Không commit hoặc in khóa ra log.
export FINANCE_JWT_SECRET="$(openssl rand -base64 32)"
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3307/$MYSQL_DATABASE"
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD="${REDIS_PASSWORD:-$MYSQL_ROOT_PASSWORD}"
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

Các lệnh trong mục IDE dùng Maven/Java trên host và chỉ dùng Compose cho database. Để chạy toàn bộ ứng dụng trong container, dùng mục Docker phía trên.

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

`sh mvnw verify` chạy kiểm thử context và integration với MySQL/MongoDB/Redis đang hoạt động và mật khẩu Redis đã cấu hình. Integration test kiểm tra đăng ký/đăng nhập, băm password, validation, JWT sai/hết hạn/giả mạo, dữ liệu khác tài khoản, và ghi giao dịch/log. Test tạo dữ liệu với username ngẫu nhiên và xóa dữ liệu đó sau khi chạy; nên dùng database phát triển riêng.

## Giao diện React

Ứng dụng có frontend tiếng Việt tại `frontend/`. Chạy backend ở cổng 8080, rồi mở terminal khác:

```bash
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:5173` để đăng ký/đăng nhập, tạo danh mục, ghi thu chi và xem tổng quan. Frontend tự quản lý Bearer token, không cần Postman. Cần Node.js 22.12+ hoặc 24 LTS. Xem [hướng dẫn frontend](frontend/README.md) để cấu hình proxy, build và chạy kiểm thử.

## Đầu tư chứng khoán

Mở **Chứng khoán** trên giao diện để ghi giao dịch mua/bán, xem nắm giữ, lịch sử, vốn đầu tư và lãi/lỗ. Module là sổ theo dõi giao dịch đã thực hiện, không đặt lệnh lên sàn và không cung cấp giá trực tiếp.

- Nhập mã (tối đa 12 ký tự, tự chuyển chữ hoa), số lượng cổ phiếu nguyên dương, giá/CP bằng VND, tổng phí và ngày khớp lệnh không ở tương lai. Ví dụ giá 95.000đ/CP phải nhập **95000**.
- Phí mua cộng vào giá vốn. Áp dụng **giá vốn bình quân gia quyền**; phí bán trừ khỏi lãi/lỗ đã chốt. Phần phí/thuế muốn tính cần nhập vào trường phí, chưa hỗ trợ cổ tức/chia tách.
- Nhập **giá tham chiếu thủ công** cho từng mã để xem giá trị và lãi/lỗ chưa chốt. Thời điểm cập nhật được hiển thị; giá không tự làm mới. Nếu còn mã đang nắm giữ thiếu giá, tổng giá trị và lãi/lỗ chưa chốt hiển thị “Chưa có giá”.
- Không cho bán vượt số cổ phiếu đang có tại ngày giao dịch. Giao dịch cùng ngày xét theo thứ tự ghi nhận. Nhập lùi ngày sẽ tính lại toàn bộ sổ; nếu gây bán vượt số lượng, toàn bộ thao tác bị hủy.
- Mỗi tài khoản có sổ và giá tham chiếu riêng. Việc mua/bán **không tự tạo giao dịch thu chi cá nhân**, tránh tính trùng. Chưa có sửa/xóa giao dịch cổ phiếu.

API đều cần Bearer token và tự lấy chủ sở hữu từ tài khoản đăng nhập:

- `GET /api/investments`: tổng hợp, các vị thế (kể cả mã đã bán hết) và lịch sử mua/bán.
- `POST /api/investments/trades`: `{"symbol":"FPT","side":"BUY","quantity":100,"price":95000,"fee":15000,"tradeDate":"2026-10-23"}`. `side` nhận `BUY` hoặc `SELL`.
- `POST /api/investments/prices`: `{"symbol":"FPT","price":98000}`.

Backend tạo hai bảng mới `stock_trades` và `stock_quotes` qua cấu hình `ddl-auto=update` hiện có. Tính tiền bằng `BigDecimal`, làm tròn kết quả đến 4 chữ số thập phân; khóa theo tài khoản khi ghi để chống bán vượt khi có yêu cầu đồng thời. Không cần thêm khóa API hoặc biến môi trường.

## Redis read cache

Docker Compose chạy thêm **Redis 7.4**, backend tự kết nối qua mạng nội bộ. Cập nhật bằng `docker compose --env-file .env.docker up -d --build --wait`; không cần tạo lại database hoặc đổi cấu hình cũ.

Cache các kết quả đọc danh mục, giao dịch (kể cả lọc khoảng ngày) và tổng hợp chứng khoán. TTL mặc định **60 giây**, khóa tách theo tài khoản và phiên bản dữ liệu; quyền truy cập được kiểm tra trước khi dùng cache. Không cache đăng nhập, token hoặc mật khẩu.

Mỗi lần ghi dữ liệu thành công, `users.cache_revision` tăng trong cùng transaction MySQL. Các request sau sử dụng khóa mới; dữ liệu cache cũ tự hết hạn. Rollback không đổi phiên bản. Cách này ngăn một request đọc đang chạy đưa dữ liệu cũ trở lại sau cập nhật, và vẫn giữ tính đúng khi Redis bị ngắt kết nối trong lúc ghi. Tất cả API ghi dữ liệu mới trong tương lai cần gọi `UserReadCache.invalidate` trong transaction tương ứng.

- `APP_CACHE_ENABLED=true` bật cache; đặt `false` để đọc trực tiếp database.
- `APP_CACHE_TTL_SECONDS=60` đặt TTL dương theo giây. Đọc cache không gia hạn TTL.
- Redis lỗi/timeout hoặc cache không đọc được: API quay về đọc database; không dùng cache để kiểm tra số cổ phiếu khi bán.
- `REDIS_PASSWORD` là mật khẩu Redis riêng, có thể thêm vào `.env.docker`. Nếu chưa có hoặc để trống, Compose dùng `MYSQL_ROOT_PASSWORD` hiện tại để tương thích cấu hình cũ. Mật khẩu không được gửi lên frontend.
- Redis giới hạn 128 MB, dùng `allkeys-lru`, không bật RDB/AOF. Dữ liệu gốc luôn ở MySQL/MongoDB; restart/xóa cache không mất dữ liệu ứng dụng.

Nếu sửa dữ liệu bằng SQL thủ công thay vì API, cache có thể giữ dữ liệu cũ tối đa TTL. Cột phiên bản cache được tạo tự động bằng cấu hình `ddl-auto=update` hiện có. Không cần thay đổi frontend.

Xem [hướng dẫn Redis trong Docker](docker/README.md#redis-cache) để kiểm tra cache. Các integration test Redis cần một Redis thật đang chạy cùng cấu hình `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`; kiểm thử mất kết nối và dữ liệu lỗi cũng có trong suite.

### Xử lý nền nhật ký giao dịch

Sau khi MySQL commit, sự kiện chứa bản sao dữ liệu giao dịch được đưa vào pool riêng
`activity-log-` (2 luồng thường trực, tối đa 4 luồng, hàng đợi 200 tác vụ).
MongoDB chậm không giữ request HTTP chờ ghi log. Giao dịch rollback không sinh log;
quyền truy cập, khóa chống bán vượt số lượng và tăng revision cache vẫn xử lý trong
transaction MySQL. Worker không nhận JPA entity hoặc phụ thuộc SecurityContext của request.

Nhật ký này là best-effort: MongoDB lỗi hoặc hàng đợi đầy sẽ ghi cảnh báo server và
bỏ log, không đổi kết quả giao dịch đã commit. Restart đột ngột có thể mất tác vụ
chưa ghi; thứ tự log không đảm bảo, dùng timestamp và transactionId để đối chiếu.
Khi tắt bình thường, pool chờ tối đa 20 giây. Nếu cần nhật ký kiểm toán đảm bảo
không mất, cần transactional outbox và cơ chế retry bền vững.

### Idempotency: chống tạo dữ liệu trùng khi gửi lại

Bốn API POST `/api/categories`, `/api/transactions`, `/api/investments/trades` và
`/api/investments/prices` nhận header tùy chọn `Idempotency-Key`. Mỗi thao tác mới
cần một khóa mới (khuyến nghị UUID); khi timeout/mất kết nối, gửi lại cùng khóa và
cùng dữ liệu. Khóa hợp lệ gồm 1–128 ký tự ASCII: chữ, số, dấu `.`, `_`, `:`, `-`.
Không gửi khóa vẫn giữ hành vi cũ, **không được bảo vệ khỏi gửi trùng**.

- Cùng tài khoản + khóa + API + DTO hợp lệ: trả lại nguyên JSON/status 200 của lần
  thành công đầu tiên, header `Idempotency-Replayed: true`. Lần đầu là `false`.
- Cùng tài khoản + khóa nhưng khác API hoặc dữ liệu: trả `409`. Thứ tự thuộc tính
  JSON không ảnh hưởng; không chuẩn hóa các khác biệt giá trị như hoa/thường.
- Khóa được tách theo tài khoản. Mọi lần thử lại vẫn cần Bearer token hợp lệ.
- Validation, thiếu quyền hoặc lỗi nghiệp vụ không lưu kết quả; transaction lỗi
  rollback cả dữ liệu lẫn bản ghi khóa. Có thể thử lại khóa đó sau khi sửa lỗi.
- Các yêu cầu đồng thời cùng tài khoản được tuần tự hóa bằng khóa hàng MySQL;
  kết quả được ghi trong **cùng transaction** với giao dịch và revision cache.
  Retry không tăng revision, ghi thêm lệnh mua/bán hoặc phát thêm sự kiện log.
  Lock timeout/lỗi tạm thời có thể yêu cầu client thử lại với cùng khóa.

Bảng `idempotency_records` được Hibernate tạo khi khởi động (`ddl-auto=update`),
được lưu trong volume MySQL hiện có. Redis không tham gia quyết định chống trùng.
Hiện không tự hết hạn/xóa khóa; không tái sử dụng khóa cũ. Bảng lưu hash khóa,
hash API/DTO, thời gian và JSON phản hồi; không lưu Bearer token hoặc request thô.
JSON phản hồi có thông tin tài khoản và dữ liệu tài chính như API gốc, cần được
bảo vệ/backup như database. Xóa tài khoản sẽ xóa bản ghi khóa qua foreign key cascade.
Nếu tự xóa bản ghi khóa, lần gửi lại sau đó có thể tạo dữ liệu mới.

Frontend tự tạo khóa, gộp các lần submit đồng thời và giữ khóa trong sessionStorage
sau lỗi mạng/5xx hoặc lỗi đọc phản hồi. Thử lại **cùng dữ liệu trong cùng phiên đăng
nhập/tab** (kể cả reload) dùng lại khóa; khi thành công hoặc lỗi 4xx xác định
(trừ 408/429), khóa được giải phóng để một thao tác mới có thể có dữ liệu giống hệt.
Nếu sửa dữ liệu hoặc đổi phiên đăng nhập sau lỗi không rõ kết quả, hãy kiểm tra
lịch sử trước khi gửi thao tác mới. Phản hồi replay là ảnh chụp lúc tạo, vì vậy
portfolio trả lại có thể cũ hơn GET `/api/investments` hiện tại.

Ví dụ Postman: chọn POST `/api/transactions`, Bearer Token như trước, thêm header
`Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000`, rồi gửi cùng JSON hai lần.
Hai phản hồi có cùng id, database chỉ có một giao dịch. Đổi `amount` nhưng giữ
khóa sẽ nhận `409`. Giao dịch khác cần UUID khác.

Bộ test case chi tiết và collection Postman tự tạo dữ liệu thử: [Kiểm thử Idempotency](docs/testing/idempotency-test-cases.md).
