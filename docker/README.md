# Chạy My Finance bằng Docker Desktop

Cần **Docker Desktop** đang chạy với **Linux containers** (Windows dùng WSL 2) và Docker Compose v2. Không cần cài Java, Maven, Node hoặc npm. Chạy các lệnh ở thư mục gốc repository, nơi có `docker-compose.yaml`.

## 1. Cập nhật mã và tạo cấu hình riêng

```powershell
git pull origin master

# Chỉ tạo lần đầu. Giữ nguyên mật khẩu/tên database đã dùng cho volume cũ.
if (-not (Test-Path .env.docker)) {
    if (Test-Path .env) { Copy-Item .env .env.docker }
    else { Copy-Item .env.example .env.docker }
}

# Sinh khóa JWT nếu chưa có, không in khóa ra màn hình và không đổi khóa đã có.
$lines = @(Get-Content .env.docker)
if (-not ($lines -match '^FINANCE_JWT_SECRET=.+$')) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $lines = @($lines | Where-Object { $_ -notmatch '^FINANCE_JWT_SECRET=' })
    $lines += 'FINANCE_JWT_SECRET=' + [Convert]::ToBase64String($bytes)
    [System.IO.File]::WriteAllLines((Join-Path (Get-Location) '.env.docker'), [string[]]$lines, [System.Text.UTF8Encoding]::new($false))
}
```

Mở `.env.docker`, kiểm tra `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MONGO_DB`. Nếu dùng file mẫu từ đầu, thay mật khẩu ví dụ bằng mật khẩu riêng. Nếu đã có volume MySQL, phải giữ mật khẩu đã khởi tạo volume đó; đổi biến môi trường không tự đổi mật khẩu trong database.

`.env.docker` được Git bỏ qua và không được gửi vào build context. Khóa JWT cần giữ ổn định khi restart; đổi khóa sẽ làm token cũ mất hiệu lực. File `.env` cũ được giữ nguyên để không làm mất cấu hình IDE đang có.

Trên Linux/macOS: copy `.env` hoặc `.env.example` sang `.env.docker`, điền cấu hình và gán `FINANCE_JWT_SECRET` bằng kết quả `openssl rand -base64 32`. Không commit file chứa khóa.

## 2. Build và chạy

```powershell
docker compose --env-file .env.docker up -d --build --wait
```

Lần đầu cần Internet để tải base images và dependencies. Các lần sau Docker tái sử dụng cache. Lệnh build tự tạo JAR và React `dist/` bên trong container; không cần build từ IDE.

Mở **http://localhost:8088**. Frontend Nginx chuyển `/api/` tới backend trong mạng Docker; đăng nhập rồi sử dụng danh mục, thu chi và chứng khoán như trước. Dùng Postman nếu muốn tại `http://localhost:8088/api/...`.

Mặc định chỉ cổng giao diện được mở trên `127.0.0.1`. API, MySQL và MongoDB không được publish ra host. Có thể đổi `APP_PORT` trong `.env.docker`, ví dụ 8090, rồi chạy lại lệnh trên. Nếu đưa ứng dụng ra Internet cần cấu hình HTTPS ở reverse proxy ngoài; cấu hình này phục vụ chạy local.

## 3. Xem trạng thái, dừng và cập nhật

```powershell
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs --tail=100 backend frontend

# Dừng container nhưng giữ dữ liệu.
docker compose --env-file .env.docker down

# Chạy lại các image đã build.
docker compose --env-file .env.docker up -d --wait

# Sau khi kéo mã mới, build lại rồi thay container.
git pull origin master
docker compose --env-file .env.docker up -d --build --wait
```

Dữ liệu ở named volumes `mysql_data` và `mongo_data`, theo Compose project của thư mục hiện tại. `down` giữ volume. **Không thêm `-v` khi muốn giữ dữ liệu.** Giữ cùng thư mục/project khi chạy lại để tiếp tục dùng volume cũ. Nếu đổi thư mục, dùng `-p` với tên project cũ.

Database trong MySQL/MongoDB cài trên Windows/IDE **không tự chuyển** sang database Docker. Nếu trước đây đã dùng các service database trong Compose cùng project, những volume đó được giữ lại. Với database nằm ngoài Docker cần export/import riêng trước khi chuyển hẳn.

## Dùng IDE hoặc công cụ database cùng Docker (tùy chọn)

```powershell
docker compose --env-file .env.docker -f docker-compose.yaml -f docker-compose.dev.yaml up -d --build --wait
```

File override mở thêm localhost: MySQL `3307`, MongoDB `27017`, backend `8080`, Redis `6379`. Dừng backend IDE trước khi mở cổng 8080 bằng Docker. Nếu chỉ cần database cho backend chạy trong IDE:

```powershell
docker compose -f docker-compose.yaml -f docker-compose.dev.yaml up -d --wait mysql mongodb redis
```

Lệnh này dùng `.env` cũ theo mặc định, không cần khóa JWT vì không chạy backend container.

## Khắc phục lỗi

- `Docker daemon is not running`: mở Docker Desktop, chờ engine sẵn sàng, kiểm tra `docker info`.
- Cổng 8088 đã dùng: đặt `APP_PORT=8090` trong `.env.docker` và chạy lại.
- Backend báo thiếu/khóa JWT sai: kiểm tra `FINANCE_JWT_SECRET` là Base64 của tối thiểu 32 byte, không có khoảng trắng.
- MySQL unhealthy hoặc access denied với volume cũ: dùng đúng mật khẩu ban đầu; không xóa volume để thử sửa.
- Không có dữ liệu cũ: kiểm tra database trước đây nằm trên Windows hay Docker và đúng Compose project/volume chưa.
- `npm`/`vite` không có trên Windows: không ảnh hưởng quy trình Docker; không chạy npm trên host.

## Cách đóng gói và kiểm chứng

Backend dùng Maven Wrapper được ghim trong repository với JDK 17, rồi chuyển JAR sang image JRE 17 chạy bằng user thường. Frontend dùng Node 24 để build và chuyển file tĩnh sang Nginx. Tests cần database nên không chạy trong bước build image (`-DskipTests`); kiểm thử luồng ứng dụng được chạy sau khi Compose sẵn sàng.

Để chạy browser tests trên một stack Docker đang hoạt động (chỉ khi phát triển, cần Node/Playwright trên host):

```powershell
cd frontend
$env:E2E_BASE_URL = 'http://127.0.0.1:8088'
npm.cmd run test:live
Remove-Item Env:E2E_BASE_URL
```

Tests tạo tài khoản/dữ liệu thử trong database phát triển. Trong môi trường cloud có proxy CA riêng, Dockerfile hỗ trợ BuildKit secret `proxy_ca` và `maven_settings` tùy chọn; cấu hình/mount riêng của phiên không được lưu vào repository hoặc image runtime. Trên Docker Desktop thông thường không cần các secret build này.

## Redis cache

Stack hiện gồm **5 service**: frontend, backend, MySQL, MongoDB và Redis. Cập nhật như các phiên bản trước:

```powershell
git pull origin master
docker compose --env-file .env.docker up -d --build --wait
```

Redis không publish cổng ra host trong cấu hình mặc định, luôn yêu cầu mật khẩu. Backend tự nhận mật khẩu từ Compose. Có thể thêm `REDIS_PASSWORD` riêng vào `.env.docker`; nếu chưa có, Compose dùng `MYSQL_ROOT_PASSWORD` sẵn có. Không cần sinh lại JWT hoặc xóa volume.

Cache có TTL 60 giây và tự đổi khóa sau cập nhật dữ liệu qua API. Có thể chỉnh `APP_CACHE_TTL_SECONDS` hoặc tắt bằng `APP_CACHE_ENABLED=false` trong `.env.docker`, rồi chạy lại Compose. Cache được bỏ qua khi Redis không kết nối được; ứng dụng vẫn dùng MySQL/MongoDB làm nguồn dữ liệu.

Vào Redis CLI mà không phải gõ mật khẩu ra màn hình:

```powershell
docker compose --env-file .env.docker exec redis sh -c 'export REDISCLI_AUTH="$REDIS_PASSWORD"; exec redis-cli'
```

Trong Redis CLI:

```text
PING
INFO stats
SCAN 0 MATCH finance:cache:v1:user:* COUNT 100
```

`PING` trả `PONG`. Mở các màn hình nhiều lần rồi xem `keyspace_hits` trong `INFO stats`. `SCAN` trả cursor và tên khóa; tiếp tục với cursor trả về nếu muốn xem thêm. Dùng `TTL <tên-khóa>` để kiểm tra thời hạn. Cache không chứa token hoặc mật khẩu người dùng.

File override `docker-compose.dev.yaml` mở thêm Redis trên `127.0.0.1:6379` cho IDE/công cụ quản trị. Khi chạy backend ngoài Docker và muốn dùng Redis này, đặt `REDIS_HOST=localhost`, `REDIS_PORT=6379`, `REDIS_PASSWORD` bằng mật khẩu đang dùng của Redis. Nếu không chạy Redis, backend tự đọc database; có thể đặt `APP_CACHE_ENABLED=false` để bỏ hẳn các lần thử kết nối.

Redis chỉ là bộ nhớ đệm, không cấu hình persistence. MySQL/MongoDB vẫn dùng các named volume hiện có; thao tác cập nhật cache không đòi hỏi xóa dữ liệu. Các request đọc cũ không được ghi lại dữ liệu vào phiên bản cache mới sau một transaction cập nhật.
