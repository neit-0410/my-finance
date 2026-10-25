import { test, expect } from "@playwright/test";

test("uncertain response retains key across reload; success allows a new identical operation", async ({ page }) => {
  const keys: string[] = [];
  await page.route("**/api/categories", async route => {
    keys.push(route.request().headers()["idempotency-key"]);
    if (keys.length === 1) return route.fulfill({ status: 503, json: { message: "Unavailable" } });
    return route.fulfill({ json: { id: 1, name: "Food", type: "EXPENSE" } });
  });
  await page.goto("/");
  const submit = () => page.evaluate(async () => {
    const source = "/src/api.ts";
    const { api } = await import(source);
    return api("/categories", { token: "fixture-token", body: { name: "Food", type: "EXPENSE" } })
      .then(() => "ok", () => "error");
  });
  expect(await submit()).toBe("error");
  await page.reload();
  expect(await submit()).toBe("ok");
  expect(keys[0]).toMatch(/^[a-f0-9-]{36}$/);
  expect(keys[1]).toBe(keys[0]);
  expect(await submit()).toBe("ok");
  expect(keys[2]).not.toBe(keys[0]);
});

test("simultaneous identical submits share a request and validation failure releases key", async ({ page }) => {
  const keys: string[] = [];
  await page.route("**/api/categories", async route => {
    keys.push(route.request().headers()["idempotency-key"]);
    await route.fulfill({ status: 400, json: { message: "Invalid" } });
  });
  await page.goto("/");
  const submit = () => page.evaluate(async () => {
    const source = "/src/api.ts";
    const { api } = await import(source);
    const options = { token: "fixture-token", body: { name: "Food", type: "EXPENSE" } };
    return Promise.allSettled([api("/categories", options), api("/categories", options)]);
  });
  await submit();
  expect(keys).toHaveLength(1);
  await submit();
  expect(keys).toHaveLength(2);
  expect(keys[1]).not.toBe(keys[0]);
});
