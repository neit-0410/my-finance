import { useState } from "react";
import type { FormEvent } from "react";
import { Check, FolderOpen, Plus, X } from "lucide-react";
import { api } from "./api";
import type { Category, Kind } from "./api";
import { Alert, Button, errorMessage, today } from "./ui";
export function TransactionForm({
  categories,
  token,
  onSaved,
  onClose,
}: {
  categories: Category[];
  token: string;
  onSaved: () => void;
  onClose: () => void;
}) {
  const [kind, setKind] = useState<Kind>("EXPENSE"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const matching = categories.filter((c) => c.type === kind);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(e.currentTarget);
    try {
      await api("/transactions", {
        token,
        body: {
          amount: Number(form.get("amount")),
          type: kind,
          categoryId: Number(form.get("categoryId")),
          description: String(form.get("description")).trim(),
          transactionDate: form.get("transactionDate"),
        },
      });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel create-panel">
      <div className="panel-heading">
        <div>
          <span className="eyebrow">GHI LẠI MỖI NGÀY</span>
          <h2>Thêm giao dịch</h2>
        </div>
        <button
          className="icon-button"
          aria-label="Đóng biểu mẫu"
          onClick={onClose}
          disabled={busy}
        >
          <X size={20} />
        </button>
      </div>
      {error && <Alert>{error}</Alert>}
      <form onSubmit={submit} aria-label="Thêm giao dịch" className="form-grid">
        <label>
          Loại giao dịch
          <select
            value={kind}
            onChange={(e) => setKind(e.target.value as Kind)}
          >
            <option value="EXPENSE">Chi tiêu</option>
            <option value="INCOME">Thu nhập</option>
          </select>
        </label>
        <label>
          Số tiền (VND)
          <input
            name="amount"
            type="number"
            min="1"
            max="999999999999"
            step="1"
            required
            placeholder="Ví dụ: 150000"
            autoFocus
          />
        </label>
        <label>
          Danh mục
          <select name="categoryId" required key={kind} defaultValue="">
            <option value="" disabled>
              Chọn danh mục
            </option>
            {matching.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Ngày giao dịch
          <input
            name="transactionDate"
            type="date"
            required
            defaultValue={today()}
          />
        </label>
        <label className="wide">
          Ghi chú <span className="optional">(không bắt buộc)</span>
          <input
            name="description"
            maxLength={255}
            placeholder="Khoản này dành cho điều gì?"
          />
        </label>
        {!matching.length && (
          <p className="form-hint wide">
            Bạn cần thêm danh mục {kind === "EXPENSE" ? "chi tiêu" : "thu nhập"}{" "}
            trong mục Danh mục trước khi lưu.
          </p>
        )}
        <div className="form-actions wide">
          <button
            type="button"
            className="secondary"
            onClick={onClose}
            disabled={busy}
          >
            Hủy
          </button>
          <Button className="primary" busy={busy} disabled={!matching.length}>
            <Check size={17} />
            Lưu giao dịch
          </Button>
        </div>
      </form>
    </section>
  );
}
export function CategoryForm({
  token,
  onSaved,
}: {
  token: string;
  onSaved: () => void;
}) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const values = new FormData(form);
    setBusy(true);
    setError("");
    try {
      await api("/categories", {
        token,
        body: {
          name: String(values.get("name")).trim(),
          type: values.get("type"),
        },
      });
      form.reset();
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel category-form">
      <div className="panel-heading">
        <h2>Danh mục mới</h2>
        <span className="soft-icon">
          <FolderOpen size={19} />
        </span>
      </div>
      <p className="muted">Đặt một cái tên dễ nhớ cho các khoản thu chi.</p>
      {error && <Alert>{error}</Alert>}
      <form onSubmit={submit} className="stack" aria-label="Tạo danh mục">
        <label>
          Tên danh mục
          <input
            name="name"
            required
            pattern=".*\S.*"
            maxLength={255}
            placeholder="Ví dụ: Ăn uống, Lương…"
          />
        </label>
        <label>
          Loại danh mục
          <select name="type">
            <option value="EXPENSE">Chi tiêu</option>
            <option value="INCOME">Thu nhập</option>
          </select>
        </label>
        <Button className="primary" busy={busy}>
          <Plus size={17} />
          Thêm danh mục
        </Button>
      </form>
    </section>
  );
}
