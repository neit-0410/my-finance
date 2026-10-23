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
