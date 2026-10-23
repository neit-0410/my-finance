import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
const token = () =>
  `test.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString("base64url")}.test`;
const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
async function mockApi(page: Page) {
  let id = 10;
  const categories = [
    { id: 1, name: "Ăn uống", type: "EXPENSE" },
    { id: 2, name: "Lương", type: "INCOME" },
  ];
  const transactions = [
    {
      id: 1,
      amount: 5000000,
      type: "INCOME",
      category: categories[1],
      transactionDate: today(),
      description: "Lương tháng này",
    },
    {
      id: 2,
      amount: 50000,
      type: "EXPENSE",
      category: categories[0],
      transactionDate: today(),
      description: "Bữa trưa",
    },
  ];
  await page.route("**/api/**", async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname;
    if (path === "/api/auth/register") {
      expect(request.headers().authorization).toBeUndefined();
      return route.fulfill({ json: { userId: 7, username: "minhan" } });
    }
    if (path === "/api/auth/login")
      return route.fulfill({
        json: { token: token(), userId: 7, username: "minhan" },
      });
    expect(request.headers().authorization).toMatch(/^Bearer /);
    if (path === "/api/categories/user/7")
      return route.fulfill({ json: categories });
    if (path === "/api/transactions/user/7")
      return route.fulfill({ json: transactions });
    if (path === "/api/categories" && request.method() === "POST") {
      const body = request.postDataJSON();
      expect(body.userId).toBeUndefined();
      expect(body.user).toBeUndefined();
      const category = { id: ++id, ...body };
      categories.push(category);
      return route.fulfill({ json: category });
    }
    if (path === "/api/transactions" && request.method() === "POST") {
      const body = request.postDataJSON();
      expect(body.user).toBeUndefined();
      expect(body.category).toBeUndefined();
      expect(typeof body.amount).toBe("number");
      const transaction = {
        id: ++id,
        ...body,
        category: categories.find((c) => c.id === body.categoryId),
      };
      transactions.push(transaction);
      return route.fulfill({ json: transaction });
    }
    return route.fulfill({ status: 404, json: {} });
  });
}
async function login(page: Page) {
  await page.goto("/");
  await page.getByLabel("Tên đăng nhập").fill("minhan");
  await page.getByLabel("Mật khẩu").fill("Test-password-123");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Tổng quan tài chính" }),
  ).toBeVisible();
  await expect(page.getByText("Đang tải dữ liệu của bạn…")).not.toBeVisible();
}

test("register, automatic login, category and transaction creation, filters, reload and logout", async ({
  page,
}, testInfo) => {
  await mockApi(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Đăng ký ngay" }).click();
  await page.getByLabel("Họ và tên").fill("Minh An");
  await page.getByLabel("Tên đăng nhập").fill("minhan");
  await page.getByLabel("Email", { exact: true }).fill("minhan@example.com");
  await page.getByLabel("Mật khẩu").fill("Test-password-123");
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Tổng quan tài chính" }),
  ).toBeVisible();
  await expect(page.locator(".stat-card.balance")).toContainText("4.950.000");
  await page.getByRole("button", { name: "Danh mục", exact: true }).click();
  await page.getByLabel("Tên danh mục").fill("Đi lại");
  await page
    .getByRole("button", { name: "Thêm danh mục", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Đi lại", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Giao dịch", exact: true }).click();
  await page
    .getByRole("button", { name: "Thêm giao dịch", exact: true })
    .click();
  await page.getByLabel("Số tiền (VND)").fill("150000");
  await page
    .getByRole("combobox", { name: "Danh mục", exact: true })
    .selectOption({ label: "Đi lại" });
  await page.getByLabel("Ghi chú").fill("Vé xe cuối tuần");
  await page.getByRole("button", { name: "Lưu giao dịch" }).click();
  await expect(
    page.getByRole("cell", { name: "Vé xe cuối tuần" }),
  ).toBeVisible();
  await page.getByLabel("Tìm giao dịch").fill("Vé xe");
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByLabel("Lọc loại giao dịch").selectOption("INCOME");
  await expect(
    page.getByRole("heading", { name: "Chưa có giao dịch nào" }),
  ).toBeVisible();
  await page.getByLabel("Tìm giao dịch").fill("");
  await page.getByLabel("Lọc loại giao dịch").selectOption("ALL");
  await page.getByLabel("Từ ngày").fill("2020-01-01");
  await page.getByLabel("Đến ngày").fill("2020-01-31");
  await expect(page.locator("tbody tr")).toHaveCount(0);
  await page.getByRole("button", { name: "Tháng này", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(3);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Tổng quan tài chính" }),
  ).toBeVisible();
  await expect(page.locator(".stat-card.balance")).toContainText("4.800.000");
  await page.screenshot({
    path: testInfo.outputPath("finance-dashboard-desktop.png"),
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Đăng xuất", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "Rất vui được gặp lại bạn" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Rất vui được gặp lại bạn" }),
  ).toBeVisible();
});

test("wrong credentials display a useful error and keep login form", async ({
  page,
}, testInfo) => {
  await page.route("**/api/auth/login", (route) =>
    route.fulfill({ status: 401, json: { message: "Invalid credentials" } }),
  );
  await page.goto("/");
  await page.getByLabel("Tên đăng nhập").fill("wrong");
  await page.getByLabel("Mật khẩu").fill("wrong-password");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText(
    "Tên đăng nhập hoặc mật khẩu không đúng.",
  );
  await expect(
    page.getByRole("button", { name: "Đăng nhập", exact: true }),
  ).toBeEnabled();
});

test("401 from a protected API clears the session", async ({
  page,
}, testInfo) => {
  await mockApi(page);
  await page.route("**/api/categories/user/7", (route) =>
    route.fulfill({ status: 401, json: {} }),
  );
  await page.goto("/");
  await page.getByLabel("Tên đăng nhập").fill("minhan");
  await page.getByLabel("Mật khẩu").fill("Test-password-123");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(
    page.getByText("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."),
  ).toBeVisible();
  expect(
    await page.evaluate(() => sessionStorage.getItem("so-nho.session")),
  ).toBeNull();
});

test("failed data load does not show misleading zero balances and can be retried", async ({
  page,
}, testInfo) => {
  await mockApi(page);
  await page.route("**/api/transactions/user/7", (route) =>
    route.fulfill({ status: 503, json: {} }),
  );
  await login(page);
  await expect(
    page.getByRole("heading", { name: "Chưa thể tải dữ liệu" }),
  ).toBeVisible();
  await expect(page.locator(".stat-card")).toHaveCount(0);
  await page.unroute("**/api/transactions/user/7");
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.locator(".stat-card.balance")).toContainText("4.950.000");
});

test("expired stored tokens return to login without API requests", async ({
  page,
}, testInfo) => {
  await page.addInitScript(() =>
    sessionStorage.setItem(
      "so-nho.session",
      JSON.stringify({
        token: "a." + btoa(JSON.stringify({ exp: 1 })) + ".b",
        userId: 7,
        username: "expired",
      }),
    ),
  );
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Rất vui được gặp lại bạn" }),
  ).toBeVisible();
});

test("mobile layout stays within viewport and category form works", async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockApi(page);
  await page.goto("/");
  await page.screenshot({
    path: testInfo.outputPath("finance-login-mobile.png"),
    fullPage: true,
  });
  await login(page);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "Danh mục", exact: true }).click();
  await page.getByLabel("Tên danh mục").fill("Tiết kiệm");
  await page.getByLabel("Loại danh mục").selectOption("INCOME");
  await page
    .getByRole("button", { name: "Thêm danh mục", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "Tiết kiệm" })).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath("finance-categories-mobile.png"),
    fullPage: true,
  });
});
