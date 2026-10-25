# Test case Idempotency

Bộ kiểm thử áp dụng cho commit có tính năng Idempotency (`f1079d3` trở lên).
Mục tiêu: mỗi thao tác có khóa chỉ ghi dữ liệu một lần; retry trả đúng phản hồi
cũ; lỗi không giữ khóa; không đọc được phản hồi của tài khoản khác.

## Chạy nhanh bằng Postman

1. Cập nhật và chạy backend có tính năng mới. Với Docker, tại thư mục gốc:

   ```powershell
   git pull origin master
   docker compose --env-file .env.docker up -d --build --wait
   ```

2. Import [idempotency.postman_collection.json](idempotency.postman_collection.json)
   vào Postman. Không cần tạo Environment hoặc tự nhập JWT.
3. Trong **Variables** của collection, đặt `baseUrl`:
   - Docker: `http://localhost:8088` (mặc định).
   - Chạy backend bằng IDE: `http://localhost:8080`.
   - Không thêm `/api` hoặc dấu `/` cuối URL.
4. Chọn **Run collection**, chạy cả hai folder theo thứ tự, **1 iteration**.
   Collection gồm 4 request chuẩn bị và 29 request test. Request cuối tự gửi thêm
   6 POST đồng thời rồi GET để kiểm tra số giao dịch.
5. Xem tab **Test Results**: mọi assertion phải pass, kể cả các ca cố ý nhận
   `400`, `401`, `403`, `409`. HTTP lỗi mong đợi không có nghĩa là test thất bại.

Mỗi lần chạy toàn bộ collection tạo hai tài khoản mới, tên `qa_idem_a_<run>` và
`qa_idem_b_<run>`, mật khẩu ngẫu nhiên và dữ liệu QA riêng. Chỉ dùng database test.
Collection không xóa dữ liệu vì ứng dụng chưa có API xóa tài khoản. Các biến runtime
chứa token/mật khẩu thử nghiệm; không chia sẻ/export chúng cùng giá trị đã điền.
Không chạy riêng request ở giữa khi chưa chạy Setup và các request phụ thuộc.
Chạy lại toàn bộ collection tạo bộ dữ liệu mới; không chạy request concurrent hai
lần trong cùng run vì các assertion đang kỳ vọng đúng một nhóm retry.

Có thể chạy cùng collection bằng Newman (cần Node.js/npm):

```powershell
npx.cmd --yes --package=newman@6.2.1 newman run docs/testing/idempotency.postman_collection.json --env-var "baseUrl=http://localhost:8088" --bail
```

Postman Desktop không cần npm. Không dùng Vite cổng 5173 cho Newman nếu Vite
chưa chạy hoặc chưa cấu hình proxy backend.

## Quy ước dữ liệu và kết quả

- A/B: hai tài khoản do Setup tạo. `tokenA`, `tokenB`, `userAId`, `userBId` và ID
  danh mục được collection tự lấy từ phản hồi, không dùng ID hard-code.
- K: khóa mới cho thao tác; retry phải giữ nguyên K. Khóa dùng chung giữa các
  endpoint trong cùng tài khoản; khác API cũng là xung đột.
- Lần đầu: `200`, `Idempotency-Replayed: false`. Retry thành công:
  `200`, `Idempotency-Replayed: true`, chuỗi JSON giống hệt phản hồi gốc.
- Đối chiếu dữ liệu bằng GET/SQL, không chỉ dựa vào status hoặc header.
- Các bước dưới đây mô tả một run mới, theo thứ tự collection.

## Test case API trong collection

| ID | Thao tác / dữ liệu | Kết quả mong đợi |
| --- | --- | --- |
| IDEM-01 | A tạo danh mục hợp lệ với K-category | 200, replay=false; có id; JSON không chứa password. |
| IDEM-02 | A gửi lại cùng API, K-category và JSON | 200, replay=true; JSON và id giống IDEM-01. |
| IDEM-03 | Đảo thứ tự các thuộc tính JSON, giữ giá trị và K-category | Replay nguyên kết quả IDEM-01. |
| IDEM-04 | Đổi tên danh mục, giữ K-category | 409; không tạo danh mục mới. |
| IDEM-05 | Gọi cập nhật giá bằng K-category đã dùng cho danh mục | 409; không tạo quote. |
| IDEM-06 | B gửi cùng K-category và JSON của A | 200, replay=false; dữ liệu thuộc B, id khác A. |
| IDEM-07 | Bỏ Authorization, dùng K-category đã có | 401; không lộ phản hồi đã lưu. |
| IDEM-08 | JWT sai, dùng K-category đã có | 401; không lộ phản hồi đã lưu. |
| IDEM-09 | Header chứa `bad key` | 400; không tạo dữ liệu/khóa. |
| IDEM-10 | Khóa dài đúng 128 ký tự ASCII hợp lệ | 200, replay=false. |
| IDEM-11 | Khóa dài 129 ký tự | 400; không tạo dữ liệu/khóa. |
| IDEM-12 | Tên danh mục rỗng, khóa K-validation mới | 400; không giữ khóa. |
| IDEM-13 | Sửa tên hợp lệ, gửi lại K-validation | 200, replay=false. |
| IDEM-14 | A tạo thu/chi 10, danh mục của A, K-cash | 200, replay=false; lưu id và toàn bộ phản hồi. |
| IDEM-15 | Gửi lại giao dịch IDEM-14 | 200, replay=true; cùng JSON/id. |
| IDEM-16 | GET danh sách giao dịch của A | Chỉ 1 giao dịch, không phải 2. |
| IDEM-17 | B tạo giao dịch dùng danh mục A, K-permission mới | 403; không ghi giao dịch/khóa. |
| IDEM-18 | B đổi sang danh mục của B, giữ K-permission | 200, replay=false. |
| IDEM-19 | Tạo danh mục không gửi Idempotency-Key | 200; lưu id thứ nhất. |
| IDEM-20 | Gửi cùng JSON IDEM-19, vẫn không có khóa | 200; id khác. Đây là hành vi tương thích, không chống trùng khi thiếu khóa. |
| IDEM-21 | A chưa có cổ phiếu, bán FPT 1, K-sell | 400 thiếu cổ phiếu; không giữ khóa. |
| IDEM-22 | A mua FPT 5, K-buy | 200, replay=false; lưu phản hồi portfolio. |
| IDEM-23 | Gửi lại lệnh mua IDEM-22 | Replay nguyên phản hồi; không tăng số cổ phiếu lên 10. |
| IDEM-24 | Gửi lại lệnh bán IDEM-21 sau khi đã mua | 200, replay=false; giữ lại 4 cổ phiếu. |
| IDEM-25 | Cập nhật giá FPT=120, K-price | 200, replay=false; lưu phản hồi. |
| IDEM-26 | Gửi lại cập nhật giá IDEM-25 | Replay nguyên JSON, kể cả timestamp giá. |
| IDEM-27 | Gửi lại lệnh mua K-buy sau bán và cập nhật giá | Trả snapshot cũ IDEM-22; không chạy mua lần nữa. |
| IDEM-28 | GET portfolio hiện tại | Có 2 lệnh, còn 4 FPT; khác snapshot lịch sử là hợp lệ. |
| IDEM-29 | 6 POST thu/chi đồng thời, cùng K-parallel/JSON; rồi GET | Cả 6 trả 200, JSON giống nhau; đúng 1 replay=false, 5 replay=true; tổng cộng A chỉ có 2 giao dịch. |

## Test bổ sung: tự động và thủ công

| ID | Chuẩn bị và bước thực hiện | Kết quả mong đợi | Cách chạy |
| --- | --- | --- | --- |
| IDEM-30 | Gửi khóa rỗng, khóa chứa `/`; sau đó thử khóa dài 1 ký tự và `valid._:-123` | Rỗng/sai ký tự: 400, không tạo dữ liệu. Khóa hợp lệ: 200, gửi lại là replay. | JUnit `keyLengthAndCharacterBoundariesAreValidatedWithoutCreatingData` |
| IDEM-31 | Cùng tài khoản/API/JSON; gửi `Case-Key` rồi `case-key` | Hai thao tác khác nhau, hai id khác nhau. | JUnit `missingKeysAndDistinctCaseSensitiveKeysAllowIntentionalDuplicatePayloads` |
| IDEM-32 | Giả lập repository lưu khóa ném lỗi ngay sau business write | 500; cả dữ liệu và revision rollback. Bỏ giả lập, gửi lại cùng khóa được 200, replay=false. | JUnit `failureToSaveKeyRollsBackBusinessDataAndAllowsSafeRetry`; không cần phá database thủ công. |
| IDEM-33 | Ghi thu/chi thành công rồi replay; đợi log nền hoàn tất | 1 row thu/chi, revision chỉ tăng một lần cho thu/chi, 1 log MongoDB. | JUnit `transactionReplayDoesNotRepeatSqlCacheInvalidationOrActivityLog` |
| IDEM-34 | Frontend gửi POST; giả lập 503; reload tab rồi gửi lại cùng dữ liệu | Hai lần dùng cùng khóa; sau thành công, thao tác giống hệt tiếp theo dùng khóa mới. | Playwright test `uncertain response retains key across reload…` |
| IDEM-35 | Frontend gọi submit đồng thời hai lần; máy chủ trả 400; submit lại | Hai lần đồng thời dùng chung một request; sau 400 lần submit mới có khóa mới. | Playwright test `simultaneous identical submits…` |
| IDEM-36 | Chạy cả collection một lần. Chọn IDEM-15, xác nhận replay; `docker compose --env-file .env.docker restart backend`, chờ backend chạy lại; Send IDEM-15 với biến cũ, không chạy Setup | Vẫn 200, replay=true, cùng id/JSON. Không xóa volume MySQL; token phải còn hạn. | Thủ công |
| IDEM-37 | Sau IDEM-36, `docker compose --env-file .env.docker restart redis`; Send IDEM-15 với khóa/JSON cũ | Vẫn replay đúng; Redis không quyết định idempotency. | Thủ công |
| IDEM-38 | Giả lập mất phản hồi sau khi server đã commit: chặn phản hồi qua proxy test; gửi lại nguyên request/K | Replay kết quả đã commit; số row/log/revision không tăng lần hai. | Thủ công với proxy lỗi; Postman timeout đơn thuần không chứng minh server đã commit. |
| IDEM-39 | Chạy 2 replica backend trỏ cùng MySQL; gửi nhóm request cùng user/K/JSON qua cả hai replica | Một bản ghi nghiệp vụ/khóa; còn lại replay sau khi lấy được lock. Nếu lock timeout, retry cùng K. | Kiểm thử hạ tầng riêng; collection và JUnit hiện dùng một backend. |

Các ca 36–39 là hướng dẫn kiểm thử vận hành, không được coi là đã chạy chỉ vì
collection/JUnit pass. Không dùng `down -v`, không xóa bảng khóa để thử restart.
Hiện hệ thống không có TTL idempotency: không có test “sau 24h tự tạo bản ghi mới”.

## Đối chiếu database sau một run Postman hoàn chỉnh

Trong MySQL Workbench, thay username bên dưới bằng giá trị `usernameA` của
collection. Đây là các câu SELECT chỉ đọc:

```sql
SET @qa_username = 'qa_idem_a_<run>';
SET @qa_user_id = (SELECT id FROM users WHERE username = @qa_username);
SELECT @qa_user_id AS qa_user_id; -- phải khác NULL
SELECT COUNT(*) AS categories_count FROM categories WHERE user_id = @qa_user_id; -- 5
SELECT COUNT(*) AS transactions_count FROM transactions WHERE user_id = @qa_user_id; -- 2
SELECT COUNT(*) AS trades_count FROM stock_trades WHERE user_id = @qa_user_id; -- 2
SELECT COUNT(*) AS quotes_count FROM stock_quotes WHERE user_id = @qa_user_id; -- 1
SELECT COUNT(*) AS idempotency_count FROM idempotency_records WHERE user_id = @qa_user_id; -- 8
SELECT cache_revision FROM users WHERE id = @qa_user_id; -- 10
SET @qa_key = '<run>-cash';
SELECT id, created_at, fingerprint
FROM idempotency_records
WHERE user_id = @qa_user_id AND key_hash = SHA2(@qa_key, 256); -- 1 row
```

Các số trên chỉ đúng cho một lần chạy nguyên collection, không Send thêm request
mới. Với B: danh mục=1, thu/chi=1, bản ghi khóa=2, revision=2.
MongoDB dùng collection `activityLog` (Spring Data mặc định). Thay ID A dạng chuỗi:

```javascript
db.activityLog.countDocuments({userId: "<userAId>", action: "CREATE_TRANSACTION"}) // 2
```

Đợi tối đa 10 giây trên môi trường test khỏe để log nền xuất hiện. Nhật ký vẫn là
best-effort: lỗi MongoDB/queue có thể mất log, không có nghĩa giao dịch SQL bị mất.
Không xóa dữ liệu tài chính thật khi dọn fixture; chỉ dọn đúng user `qa_idem_*`
của lần test đã xác định, sau khi kiểm tra các quan hệ phụ thuộc.

## Chạy kiểm thử tự động trong repository

Cần Java 17, MySQL/MongoDB/Redis test và cấu hình kết nối như README gốc:

```powershell
cd backend
.\mvnw.cmd -Dtest=IdempotencyIntegrationTests verify
```

Class này có 8 integration test, tạo fixture riêng và tự xóa sau mỗi test. Linux
hoặc Git Bash dùng `sh mvnw -Dtest=IdempotencyIntegrationTests verify`.
Không trỏ integration test vào database sản xuất.

Frontend (cần Node.js, dependencies đã cài; Playwright/Chromium sẵn sàng):

```powershell
cd frontend
npm.cmd exec -- playwright test e2e/idempotency.spec.ts
```

Hai ca frontend dùng API giả lập để chủ động tạo lỗi 503/400, không thay thế kiểm
thử backend thật. Ca fault injection repository cũng chỉ chạy trong context test.
Tiêu chí đạt: không có assertion lỗi, số row/revision đúng, snapshot replay đúng,
không rò phản hồi giữa tài khoản. Ghi riêng kết quả các ca vận hành chạy thủ công.

## Kết quả xác minh ngày 25/10/2026

- JUnit `IdempotencyIntegrationTests`: **8 test pass**, build thành công.
- Collection chạy bằng Newman 6.2.1 trên backend thật ở cổng test 18089:
  **33 request trong collection + 7 request từ script = 40 HTTP request**,
  **70 assertion pass**, không có assertion lỗi.
- Đối chiếu trực tiếp MySQL cho A/B: số danh mục, thu/chi, lệnh mua/bán, quote,
  bản ghi idempotency và cache revision khớp toàn bộ số kỳ vọng ở phần SQL.
- Các ca vận hành IDEM-36–39 chưa thực hiện trong lượt xác minh này. Hai ca
  Playwright IDEM-34–35 có sẵn; lượt này kiểm tra collection và integration test,
  không chạy lại Playwright.
