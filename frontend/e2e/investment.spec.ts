import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
async function enter(page: Page) {
  const token = `test.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString("base64url")}.test`;
  await page.addInitScript(
    ({ token }) =>
      sessionStorage.setItem(
        "so-nho.session",
        JSON.stringify({ token, userId: 7, username: "investor" }),
      ),
    { token },
  );
  let snapshot = {
    costBasis: 0,
    marketValue: 0 as number | null,
    unrealizedPnl: 0 as number | null,
    realizedPnl: 0,
    missingPrices: 0,
    positions: [] as any[],
    trades: [] as any[],
  };
  await page.route("**/api/**", async (route) => {
    const req = route.request(),
      path = new URL(req.url()).pathname;
    expect(req.headers().authorization).toMatch(/^Bearer /);
    if (
      path === "/api/categories/user/7" ||
      path === "/api/transactions/user/7"
    )
      return route.fulfill({ json: [] });
    if (path === "/api/investments/trades") {
      const body = req.postDataJSON();
      expect(body.userId).toBeUndefined();
      if (body.side === "SELL")
        return route.fulfill({
          status: 400,
          json: {
            message:
              "Không đủ cổ phiếu FPT tại ngày giao dịch. Kiểm tra số lượng và thứ tự ngày mua/bán.",
          },
        });
      snapshot = {
        costBasis: 100100,
        marketValue: null,
        unrealizedPnl: null,
        realizedPnl: 0,
        missingPrices: 1,
        positions: [
          {
            symbol: body.symbol,
            quantity: 100,
            averageCost: 1001,
            costBasis: 100100,
            currentPrice: null,
            priceUpdatedAt: null,
            marketValue: null,
            unrealizedPnl: null,
            realizedPnl: 0,
          },
        ],
        trades: [
          {
            id: 1,
            ...body,
            price: Number(body.price),
            fee: Number(body.fee),
            realizedPnl: 0,
          },
        ],
      };
    }
    if (path === "/api/investments/prices") {
      const body = req.postDataJSON();
      expect(body.symbol).toBe("FPT");
      expect(Number(body.price)).toBe(1200);
      snapshot = {
        ...snapshot,
        marketValue: 120000,
        unrealizedPnl: 19900,
        missingPrices: 0,
        positions: snapshot.positions.map((p) => ({
          ...p,
          currentPrice: 1200,
          marketValue: 120000,
          unrealizedPnl: 19900,
          priceUpdatedAt: new Date().toISOString(),
        })),
      };
    }
    return route.fulfill({ json: snapshot });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Chứng khoán", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Đầu tư chứng khoán" }),
  ).toBeVisible();
  await expect(page.getByText("Đang tải danh mục đầu tư…")).not.toBeVisible();
}

test("investment buy, manual quote and trade history", async ({
  page,
}, testInfo) => {
  await enter(page);
  await expect(
    page.getByRole("heading", { name: "Danh mục đầu tư đang trống" }),
  ).toBeVisible();
  await page.getByLabel("Mã cổ phiếu", { exact: true }).fill("fpt");
  await page.getByLabel("Số lượng cổ phiếu").fill("100");
  await page.getByLabel("Giá mỗi cổ phiếu (VND)").fill("1000");
  await page.getByLabel("Phí giao dịch (VND)").fill("100");
  await page.getByRole("button", { name: "Lưu giao dịch cổ phiếu" }).click();
  await expect(page.locator(".investment-stats").first()).toContainText(
    "100.100",
  );
  await expect(page.getByText("1 mã đang thiếu giá tham chiếu")).toBeVisible();
  await page
    .getByRole("combobox", { name: "Mã cần cập nhật" })
    .selectOption("FPT");
  await page.getByLabel("Giá tham chiếu (VND/CP)").fill("1200");
  await page.getByRole("button", { name: "Cập nhật giá", exact: true }).click();
  await expect(page.locator(".investment-stats")).toContainText("120.000");
  await expect(page.locator(".investment-stats")).toContainText("19.900");
  await page.getByRole("tab", { name: /Lịch sử mua\/bán/ }).click();
  await expect(
    page.getByRole("cell", { name: "Mua", exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("investment-desktop.png"),
    fullPage: true,
  });
});

test("mobile layout and rejected oversell keep form data", async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await enter(page);
  await page.getByRole("combobox", { name: "Loại lệnh" }).selectOption("SELL");
  await page.getByLabel("Mã cổ phiếu", { exact: true }).fill("FPT");
  await page.getByLabel("Số lượng cổ phiếu").fill("100");
  await page.getByLabel("Giá mỗi cổ phiếu (VND)").fill("1000");
  await page.getByRole("button", { name: "Lưu giao dịch cổ phiếu" }).click();
  await expect(page.getByRole("alert")).toContainText("Không đủ cổ phiếu");
  await expect(page.getByLabel("Số lượng cổ phiếu")).toHaveValue("100");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath("investment-mobile.png"),
    fullPage: true,
  });
});
