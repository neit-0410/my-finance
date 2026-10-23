import { useState } from "react";
import type { FormEvent } from "react";
import {
  ArrowRight,
  ArrowUpRight,
  Leaf,
  ShieldCheck,
  Wallet,
} from "lucide-react";
import { api } from "./api";
import type { Session } from "./api";
import { Alert, Brand, Button, errorMessage } from "./ui";
export default function Auth({
  onLogin,
  notice,
}: {
  onLogin: (session: Session) => void;
  notice: string;
}) {
  const [register, setRegister] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [info, setInfo] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setInfo("");
    setBusy(true);
    const data = new FormData(event.currentTarget);
    const username = String(data.get("username")).trim(),
      password = String(data.get("password"));
    try {
      if (new TextEncoder().encode(password).length > 72)
        throw new Error("Mật khẩu không được vượt quá 72 byte UTF-8.");
      if (register) {
        await api("/auth/register", {
          body: {
            username,
            password,
            email: String(data.get("email")).trim(),
            fullName: String(data.get("fullName")).trim(),
          },
        });
        setRegister(false);
        setInfo("Tạo tài khoản thành công. Đang đăng nhập…");
      }
      onLogin(
        await api<Session>("/auth/login", { body: { username, password } }),
      );
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Brand />
        <div className="auth-copy">
          <span className="eyebrow light">MỖI NGÀY MỘT CHÚT, AN TÂM HƠN</span>
          <h1>
            Tiền của bạn.
            <br />
            Kế hoạch của bạn.
          </h1>
          <p>
            Hiểu rõ dòng tiền, vun vén những điều quan trọng.
            <br />
            Bắt đầu từ những khoản thu chi nhỏ nhất.
          </p>
          <div className="illustration" aria-hidden="true">
            <div className="illustration-top">
              <span>
                <Wallet size={20} /> Một thói quen tốt
              </span>
              <span>•••</span>
            </div>
            <h3>Tích lũy từng ngày</h3>
            <div className="illustration-chart">
              {[24, 39, 30, 53, 45, 67, 84, 100].map((h, i) => (
                <span key={i} style={{ height: `${h}%` }} />
              ))}
            </div>
            <div className="illustration-bottom">
              <span>Những bước nhỏ</span>
              <span>
                Tương lai lớn <ArrowUpRight size={16} />
              </span>
            </div>
            <div className="floating-leaf">
              <Leaf size={28} />
            </div>
          </div>
          <p className="story-note">Biểu đồ minh họa hành trình tích lũy.</p>
        </div>
        <div className="auth-footer">
          <ShieldCheck size={16} /> Không gian tài chính riêng của bạn
        </div>
      </section>
      <section className="auth-form-area">
        <div className="auth-mobile-brand">
          <Brand />
        </div>
        <div className="auth-form-box">
          <span className="eyebrow">CHÀO MỪNG ĐẾN VỚI SỔ NHỎ</span>
          <h2>
            {register
              ? "Bắt đầu một thói quen tốt"
              : "Rất vui được gặp lại bạn"}
          </h2>
          <p className="muted">
            {register
              ? "Tạo tài khoản để quản lý tài chính theo cách của bạn."
              : "Đăng nhập để tiếp tục chăm sóc tài chính của mình."}
          </p>
          {notice && (
            <div className="notice" role="status">
              {notice}
            </div>
          )}
          {info && (
            <div className="notice" role="status">
              {info}
            </div>
          )}
          {error && <Alert>{error}</Alert>}
          <form
            onSubmit={submit}
            className="stack"
            aria-label={register ? "Đăng ký tài khoản" : "Đăng nhập"}
          >
            {register && (
              <label>
                Họ và tên
                <input
                  name="fullName"
                  autoComplete="name"
                  maxLength={255}
                  placeholder="Nguyễn Minh An"
                />
              </label>
            )}
            <label>
              Tên đăng nhập
              <input
                name="username"
                required
                pattern=".*\S.*"
                minLength={register ? 3 : 1}
                maxLength={64}
                autoComplete="username"
                placeholder="Tên đăng nhập của bạn"
              />
            </label>
            {register && (
              <label>
                Email
                <input
                  name="email"
                  required
                  type="email"
                  maxLength={254}
                  autoComplete="email"
                  placeholder="ban@example.com"
                />
              </label>
            )}
            <label>
              Mật khẩu
              <input
                name="password"
                required
                type="password"
                minLength={register ? 8 : 1}
                maxLength={72}
                autoComplete={register ? "new-password" : "current-password"}
                placeholder={
                  register ? "Tối thiểu 8 ký tự" : "Nhập mật khẩu của bạn"
                }
              />
            </label>
            <Button className="primary auth-submit" busy={busy}>
              {register ? "Tạo tài khoản" : "Đăng nhập"}
              <ArrowRight size={18} />
            </Button>
          </form>
          <p className="auth-switch">
            {register ? "Bạn đã có tài khoản?" : "Chưa có tài khoản?"}{" "}
            <button
              className="text-button"
              disabled={busy}
              onClick={() => {
                setRegister(!register);
                setError("");
                setInfo("");
              }}
            >
              {register ? "Đăng nhập" : "Đăng ký ngay"}
            </button>
          </p>
          <div className="privacy-note">
            <ShieldCheck size={17} />
            <span>Mật khẩu được băm. Dữ liệu chỉ thuộc về bạn.</span>
          </div>
        </div>
        <p className="auth-bottom">
          Sổ nhỏ · Rõ ràng hôm nay, vững vàng ngày mai.
        </p>
      </section>
    </main>
  );
}
