export type Kind = "INCOME" | "EXPENSE";
export interface Session {
  token: string;
  userId: number;
  username: string;
}
export interface Category {
  id: number;
  name: string;
  type: Kind;
}
export interface Transaction {
  id: number;
  amount: number;
  type: Kind;
  category: Category;
  description: string | null;
  transactionDate: string;
}
export const SESSION_KEY = "so-nho.session";
export function tokenExpiry(token: string): number {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const { exp } = JSON.parse(atob(payload));
    return typeof exp === "number" ? exp * 1000 : 0;
  } catch {
    return 0;
  }
}
export function readSession(): Session | null {
  try {
    const s = JSON.parse(sessionStorage.getItem(SESSION_KEY) || "null");
    if (
      s &&
      typeof s.token === "string" &&
      Number.isSafeInteger(s.userId) &&
      s.userId > 0 &&
      typeof s.username === "string" &&
      tokenExpiry(s.token) > Date.now()
    )
      return s;
    sessionStorage.removeItem(SESSION_KEY);
  } catch {
    /* Disabled storage must not prevent login. */
  }
  return null;
}
// Keep the same key after an uncertain network/server failure, including a reload.
const inFlight = new Map<string, Promise<unknown>>();
const pendingKeys = new Map<string, string>();
export async function api<T>(
  path: string,
  options: { token?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<T> {
  const protectedWrite = !!options.token && options.body !== undefined &&
    ["/categories", "/transactions", "/investments/trades", "/investments/prices"].includes(path);
  if (!protectedWrite) return send<T>(path, options);
  const bytes = new TextEncoder().encode(options.token + "\n" + path + "\n" + JSON.stringify(options.body));
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  const slot = "so-nho.pending." + Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
  const running = inFlight.get(slot);
  if (running) return running as Promise<T>;
  let key = pendingKeys.get(slot);
  try { key = sessionStorage.getItem(slot) || key; } catch { /* memory fallback */ }
  key ||= crypto.randomUUID();
  pendingKeys.set(slot, key);
  try { sessionStorage.setItem(slot, key); } catch { /* memory fallback */ }
  const clear = () => {
    pendingKeys.delete(slot);
    try { sessionStorage.removeItem(slot); } catch { /* disabled storage */ }
  };
  const promise = send<T>(path, options, key, clear).finally(() => inFlight.delete(slot));
  inFlight.set(slot, promise);
  return promise;
}
async function send<T>(
  path: string,
  options: { token?: string; body?: unknown; signal?: AbortSignal },
  key?: string,
  clear?: () => void,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method: options.body === undefined ? "GET" : "POST",
      headers: {
        ...(key ? { "Idempotency-Key": key } : {}),
        ...(options.body !== undefined
          ? { "Content-Type": "application/json" }
          : {}),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      },
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError")
      throw error;
    throw new Error(key
      ? "Chưa xác nhận được kết quả. Hãy giữ nguyên thông tin và thử lại, hoặc kiểm tra lịch sử trước khi thay đổi dữ liệu."
      : "Không thể kết nối máy chủ. Vui lòng thử lại sau.");
  }
  if (!response.ok) {
    if (response.status >= 400 && response.status < 500 && ![408, 429].includes(response.status)) clear?.();
    if (response.status === 401 && options.token)
      window.dispatchEvent(new Event("session-expired"));
    const messages: Record<number, string> = {
      400: "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường.",
      401: options.token
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : "Tên đăng nhập hoặc mật khẩu không đúng.",
      403: "Bạn không có quyền truy cập dữ liệu này.",
      404: "Không tìm thấy dữ liệu. Vui lòng tải lại trang.",
      409: path.startsWith("/auth")
        ? "Tên đăng nhập hoặc email đã được sử dụng."
        : "Khóa yêu cầu đã được dùng cho nội dung khác. Vui lòng kiểm tra lại dữ liệu.",
    };
    if (path.startsWith("/investments") && response.status === 400) {
      const detail = await response.json().catch(() => null);
      if (
        typeof detail?.message === "string" &&
        detail.message.startsWith("Không đủ cổ phiếu")
      )
        throw new Error(detail.message);
    }
    throw new Error(
      messages[response.status] || (key
        ? "Chưa xác nhận được kết quả. Hãy giữ nguyên thông tin và thử lại, hoặc kiểm tra lịch sử trước khi thay đổi dữ liệu."
        : "Máy chủ chưa xử lý được yêu cầu. Vui lòng thử lại sau."),
    );
  }
  const result = response.status === 204 ? undefined as T : await response.json() as T;
  clear?.();
  return result;
}
