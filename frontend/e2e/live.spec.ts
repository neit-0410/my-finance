import { test, expect } from "@playwright/test";

test("real backend: register, create category and transaction, verify persistence after reload", async ({
  page,
}, testInfo) => {
  const username = `ui_${Date.now()}`;
  await page.goto("/");
  await page.getByRole("button", { name: "Đăng ký ngay" }).click();
  await page.getByLabel("Họ và tên").fill("Frontend Integration");
  await page.getByLabel("Tên đăng nhập").fill(username);
  await page
    .getByLabel("Email", { exact: true })
    .fill(`${username}@example.com`);
  await page.getByLabel("Mật khẩu").fill("Integration-test-123");
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Tổng quan tài chính" }),
  ).toBeVisible();
  await expect(page.getByText("Đang tải dữ liệu của bạn…")).not.toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await page.getByRole("button", { name: "Danh mục", exact: true }).click();
  await page.getByLabel("Tên danh mục").fill("Ăn uống");
  await page
    .getByRole("button", { name: "Thêm danh mục", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Ăn uống", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Giao dịch", exact: true }).click();
  await page
    .getByRole("button", { name: "Thêm giao dịch", exact: true })
    .click();
  await page.getByLabel("Số tiền (VND)").fill("125000");
  await page
    .getByRole("combobox", { name: "Danh mục", exact: true })
    .selectOption({ label: "Ăn uống" });
  await page.getByLabel("Ghi chú").fill("Bữa trưa kiểm thử giao diện");
  await page.getByRole("button", { name: "Lưu giao dịch" }).click();
  await expect(
    page.getByRole("cell", { name: "Bữa trưa kiểm thử giao diện" }),
  ).toBeVisible();
  await page.reload();
  await expect(page.locator(".stat-card.balance")).toContainText("125.000");
  await expect(
    page.getByRole("cell", { name: "Bữa trưa kiểm thử giao diện" }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("finance-live-desktop.png"),
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Đăng xuất", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "Rất vui được gặp lại bạn" }),
  ).toBeVisible();
});

test("real backend: stock buy, manual price, sale and persisted profit", async ({
  page,
}, testInfo) => {
  const username = `stockui_${Date.now()}`;
  await page.goto("/");
  await page.getByRole("button", { name: "Đăng ký ngay" }).click();
  await page.getByLabel("Tên đăng nhập").fill(username);
  await page
    .getByLabel("Email", { exact: true })
    .fill(`${username}@example.com`);
  await page.getByLabel("Mật khẩu").fill("Integration-test-123");
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Tổng quan tài chính" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Chứng khoán", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Danh mục đầu tư đang trống" }),
  ).toBeVisible();
  await page.getByLabel("Mã cổ phiếu", { exact: true }).fill("fpt");
  await page.getByLabel("Số lượng cổ phiếu").fill("100");
  await page.getByLabel("Giá mỗi cổ phiếu (VND)").fill("1000");
  await page.getByLabel("Phí giao dịch (VND)").fill("100");
  await page.getByRole("button", { name: "Lưu giao dịch cổ phiếu" }).click();
  await expect(page.getByText("1 mã đang thiếu giá tham chiếu")).toBeVisible();
  await expect(page.locator(".investment-stats")).toContainText("100.100");
  await page
    .getByRole("combobox", { name: "Mã cần cập nhật" })
    .selectOption("FPT");
  await page.getByLabel("Giá tham chiếu (VND/CP)").fill("1200");
  await page.getByRole("button", { name: "Cập nhật giá", exact: true }).click();
  await expect(page.locator(".investment-stats")).toContainText("19.900");
  await page.getByRole("combobox", { name: "Loại lệnh" }).selectOption("SELL");
  await page.getByLabel("Mã cổ phiếu", { exact: true }).fill("FPT");
  await page.getByLabel("Số lượng cổ phiếu").fill("40");
  await page.getByLabel("Giá mỗi cổ phiếu (VND)").fill("1400");
  await page.getByLabel("Phí giao dịch (VND)").fill("20");
  await page.getByRole("button", { name: "Lưu giao dịch cổ phiếu" }).click();
  await expect(page.locator(".investment-stats")).toContainText("15.940");
  await expect(page.locator(".investment-stats")).toContainText("11.940");
  await page.getByRole("tab", { name: /Lịch sử mua\/bán/ }).click();
  await expect(
    page.getByRole("cell", { name: "Bán", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Tổng quan", exact: true }).click();
  await expect(page.locator(".stat-card.balance")).toContainText("0");
  await page.getByRole("button", { name: "Chứng khoán", exact: true }).click();
  await expect(page.locator(".investment-stats")).toContainText("15.940");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: testInfo.outputPath("investment-live.png"),
    fullPage: true,
  });
});
