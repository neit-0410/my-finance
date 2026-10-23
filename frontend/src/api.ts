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
export async function api<T>(
  path: string,
  options: { token?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method: options.body === undefined ? "GET" : "POST",
      headers: {
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
    throw new Error("Không thể kết nối máy chủ. Vui lòng thử lại sau.");
  }
  if (!response.ok) {
    if (response.status === 401 && options.token)
      window.dispatchEvent(new Event("session-expired"));
    const messages: Record<number, string> = {
      400: "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường.",
      401: options.token
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : "Tên đăng nhập hoặc mật khẩu không đúng.",
      403: "Bạn không có quyền truy cập dữ liệu này.",
      404: "Không tìm thấy dữ liệu. Vui lòng tải lại trang.",
      409: "Tên đăng nhập hoặc email đã được sử dụng.",
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
      messages[response.status] ||
        "Máy chủ chưa xử lý được yêu cầu. Vui lòng thử lại sau.",
    );
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
