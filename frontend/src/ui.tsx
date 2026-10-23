import type { ButtonHTMLAttributes, ReactNode } from "react";
import { CircleAlert, LoaderCircle, Wallet } from "lucide-react";
export function Brand() {
  return (
    <div className="brand">
      <span className="brand-symbol">
        <Wallet size={23} />
      </span>
      <span>
        Sổ nhỏ<span className="brand-dot">.</span>
      </span>
    </div>
  );
}
export function Button({
  children,
  busy,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { busy?: boolean }) {
  return (
    <button {...props} disabled={props.disabled || busy}>
      {busy && <LoaderCircle size={17} className="spin" />}
      {children}
    </button>
  );
}
export function Alert({ children }: { children: ReactNode }) {
  return (
    <div className="alert" role="alert">
      <CircleAlert size={18} />
      <span>{children}</span>
    </div>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <Wallet size={25} />
      </span>
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}
export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : "Đã xảy ra lỗi. Vui lòng thử lại.";
export const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);
export function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export const today = () => localDate(new Date());
export function monthRange() {
  const d = new Date();
  return {
    start: localDate(new Date(d.getFullYear(), d.getMonth(), 1)),
    end: localDate(new Date(d.getFullYear(), d.getMonth() + 1, 0)),
  };
}
