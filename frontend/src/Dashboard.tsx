import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  Check,
  ChevronRight,
  CreditCard,
  FolderOpen,
  LayoutDashboard,
  Leaf,
  LoaderCircle,
  LogOut,
  Plus,
  Search,
  ShieldCheck,
  Wallet,
  TrendingUp,
} from "lucide-react";
import { api } from "./api";
import type { Category, Session, Transaction } from "./api";
import { Alert, Brand, Empty, errorMessage, money, monthRange } from "./ui";
import { CategoryForm, TransactionForm } from "./Forms";
import { Cashflow, TransactionTable } from "./Charts";
import Investments from "./Investments";
type Page = "overview" | "transactions" | "categories" | "investments";
export default function Dashboard({
  session,
  logout,
}: {
  session: Session;
  logout: (notice?: string) => void;
}) {
  const [page, setPage] = useState<Page>("overview"),
    [categories, setCategories] = useState<Category[]>([]),
    [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [success, setSuccess] = useState(""),
    [showForm, setShowForm] = useState(false);
  const [range, setRange] = useState(monthRange),
    [query, setQuery] = useState(""),
    [kind, setKind] = useState("ALL");
  const rangeValid = Boolean(
    range.start && range.end && range.start <= range.end,
  );
  const load = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setError("");
      try {
        const [c, t] = await Promise.all([
          api<Category[]>(`/categories/user/${session.userId}`, {
            token: session.token,
            signal,
          }),
          api<Transaction[]>(`/transactions/user/${session.userId}`, {
            token: session.token,
            signal,
          }),
        ]);
        if (!signal?.aborted) {
          setCategories(c);
          setTransactions(t);
        }
      } catch (err) {
        if (!signal?.aborted) setError(errorMessage(err));
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [session],
  );
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);
  useEffect(() => {
    if (!success) return;
    const timer = window.setTimeout(() => setSuccess(""), 5000);
    return () => clearTimeout(timer);
  }, [success]);
  const inRange = useMemo(
    () =>
      transactions
        .filter(
          (t) =>
            rangeValid &&
            t.transactionDate >= range.start &&
            t.transactionDate <= range.end,
        )
        .sort(
          (a, b) =>
            b.transactionDate.localeCompare(a.transactionDate) || b.id - a.id,
        ),
    [transactions, range, rangeValid],
  );
  const filtered = inRange.filter(
    (t) =>
      (kind === "ALL" || t.type === kind) &&
      `${t.description || ""} ${t.category?.name || ""}`
        .toLocaleLowerCase("vi")
        .includes(query.toLocaleLowerCase("vi")),
  );
  const income = inRange
      .filter((t) => t.type === "INCOME")
      .reduce((s, t) => s + t.amount, 0),
    expense = inRange
      .filter((t) => t.type === "EXPENSE")
      .reduce((s, t) => s + t.amount, 0);
  const expenses = categories
    .map((c) => ({
      ...c,
      total: inRange
        .filter((t) => t.type === "EXPENSE" && t.category?.id === c.id)
        .reduce((s, t) => s + t.amount, 0),
    }))
    .filter((c) => c.total > 0)
    .sort((a, b) => b.total - a.total);
  const titles = {
    overview: ["Tổng quan tài chính", "Một chút rõ ràng, một ngày an tâm."],
    transactions: ["Giao dịch của bạn", "Những khoản nhỏ, một bức tranh lớn."],
    categories: ["Danh mục thu chi", "Sắp xếp gọn gàng, quản lý dễ dàng."],
    investments: [
      "Đầu tư chứng khoán",
      "Theo dõi danh mục, vững vàng từng quyết định.",
    ],
  };
  const nav = [
    { id: "overview" as Page, icon: LayoutDashboard, label: "Tổng quan" },
    { id: "transactions" as Page, icon: CreditCard, label: "Giao dịch" },
    { id: "categories" as Page, icon: FolderOpen, label: "Danh mục" },
    { id: "investments" as Page, icon: TrendingUp, label: "Chứng khoán" },
  ];
  function navigate(value: Page) {
    setPage(value);
    setShowForm(false);
    setSuccess("");
  }
  return (
    <div className="app-layout">
      <aside className="sidebar">
        <Brand />
        <div className="sidebar-label">KHÔNG GIAN CỦA BẠN</div>
        <nav aria-label="Điều hướng chính">
          {nav.map(({ id, icon: Icon, label }) => (
            <button
              key={id}
              className={`nav-link ${page === id ? "active" : ""}`}
              aria-current={page === id ? "page" : undefined}
              onClick={() => navigate(id)}
            >
              <Icon size={20} />
              {label}
              {page === id && <span className="nav-active-dot" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-tip">
            <span className="tip-icon">
              <Leaf size={22} />
            </span>
            <strong>Tích lũy từ hôm nay</strong>
            <p>
              Hiểu từng khoản nhỏ.
              <br />
              Chạm những mục tiêu lớn.
            </p>
          </div>
          <button className="logout" onClick={() => logout()}>
            <LogOut size={18} />
            Đăng xuất
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            Không gian cá nhân
            <ChevronRight size={15} />
            <span>{nav.find((n) => n.id === page)?.label}</span>
          </div>
          <div className="account">
            <span className="account-avatar">
              {session.username.slice(0, 1).toUpperCase()}
            </span>
            <div>
              <strong>{session.username}</strong>
              <span>Tài khoản cá nhân</span>
            </div>
            <button
              className="mobile-logout icon-button"
              aria-label="Đăng xuất"
              onClick={() => logout()}
            >
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main className="main-content">
          <div className="page-heading">
            <div>
              <span className="eyebrow">TÀI CHÍNH TRONG TẦM TAY</span>
              <h1>{titles[page][0]}</h1>
              <p className="muted">{titles[page][1]}</p>
            </div>
            {page !== "categories" && page !== "investments" && (
              <button
                className="primary"
                disabled={loading || Boolean(error)}
                onClick={() => setShowForm(true)}
              >
                <Plus size={18} />
                Thêm giao dịch
              </button>
            )}
          </div>
          {page === "investments" ? (
            <Investments session={session} />
          ) : (
            <>
              {success && (
                <div className="toast" role="status">
                  <Check size={18} />
                  {success}
                </div>
              )}
              {error && (
                <div className="error-row">
                  <Alert>{error}</Alert>
                  <button className="secondary" onClick={() => void load()}>
                    Thử lại
                  </button>
                </div>
              )}
              {showForm && (
                <TransactionForm
                  categories={categories}
                  token={session.token}
                  onClose={() => setShowForm(false)}
                  onSaved={() => {
                    setShowForm(false);
                    setSuccess("Đã lưu giao dịch thành công.");
                    void load();
                  }}
                />
              )}
              {page !== "categories" && (
                <div className="period-row">
                  <span className="period-title">
                    <CalendarDays size={17} />
                    Khoảng thời gian
                  </span>
                  <div className="date-range">
                    <label className="sr-only" htmlFor="start-date">
                      Từ ngày
                    </label>
                    <input
                      id="start-date"
                      type="date"
                      value={range.start}
                      onChange={(e) =>
                        setRange({ ...range, start: e.target.value })
                      }
                    />
                    <span>—</span>
                    <label className="sr-only" htmlFor="end-date">
                      Đến ngày
                    </label>
                    <input
                      id="end-date"
                      type="date"
                      value={range.end}
                      onChange={(e) =>
                        setRange({ ...range, end: e.target.value })
                      }
                    />
                  </div>
                  <button
                    className="text-button"
                    onClick={() => setRange(monthRange())}
                  >
                    Tháng này
                  </button>
                  {!rangeValid && (
                    <span className="field-error" role="alert">
                      Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.
                    </span>
                  )}
                </div>
              )}
              {loading ? (
                <div className="loading" role="status">
                  <LoaderCircle className="spin" />
                  Đang tải dữ liệu của bạn…
                </div>
              ) : error ? (
                <section className="panel">
                  <Empty title="Chưa thể tải dữ liệu">
                    Vui lòng thử lại để xem thông tin tài chính của bạn.
                  </Empty>
                </section>
              ) : (
                <>
                  {page === "overview" && (
                    <>
                      <div className="stat-grid">
                        <section className="stat-card balance">
                          <div>
                            <span>Chênh lệch thu chi</span>
                            <span className="stat-icon">
                              <Wallet size={20} />
                            </span>
                          </div>
                          <strong>{money(income - expense)}</strong>
                          <p>
                            Trong khoảng thời gian đã chọn
                            <ArrowUpRight size={16} />
                          </p>
                        </section>
                        <section className="stat-card">
                          <div>
                            <span>Tổng thu nhập</span>
                            <span className="stat-icon income">
                              <ArrowDownLeft size={20} />
                            </span>
                          </div>
                          <strong>{money(income)}</strong>
                          <p>
                            <span className="status-dot income-dot" />
                            Những khoản tiền bạn nhận được
                          </p>
                        </section>
                        <section className="stat-card">
                          <div>
                            <span>Tổng chi tiêu</span>
                            <span className="stat-icon expense">
                              <ArrowUpRight size={20} />
                            </span>
                          </div>
                          <strong>{money(expense)}</strong>
                          <p>
                            <span className="status-dot expense-dot" />
                            Những khoản tiền bạn đã chi
                          </p>
                        </section>
                      </div>
                      <div className="insight-grid">
                        <Cashflow
                          items={inRange}
                          start={rangeValid ? range.start : monthRange().start}
                          end={rangeValid ? range.end : monthRange().end}
                        />
                        <section className="panel breakdown">
                          <div className="panel-heading">
                            <div>
                              <h2>Chi tiêu theo danh mục</h2>
                              <p className="muted">Tiền của bạn đang đi đâu?</p>
                            </div>
                          </div>
                          {expenses.length ? (
                            <>
                              <div className="breakdown-total">
                                <span>Tổng chi tiêu</span>
                                <strong>{money(expense)}</strong>
                              </div>
                              <div className="segments" aria-hidden="true">
                                {expenses.map((c, i) => (
                                  <span
                                    key={c.id}
                                    style={{
                                      width: `${(c.total / expense) * 100}%`,
                                      background: [
                                        "#176b50",
                                        "#72aa92",
                                        "#adc9bb",
                                        "#d0dfd5",
                                      ][i % 4],
                                    }}
                                  />
                                ))}
                              </div>
                              <div className="breakdown-list">
                                {expenses.map((c, i) => (
                                  <div key={c.id}>
                                    <span>
                                      <i
                                        style={{
                                          background: [
                                            "#176b50",
                                            "#72aa92",
                                            "#adc9bb",
                                            "#d0dfd5",
                                          ][i % 4],
                                        }}
                                      />
                                      {c.name}
                                    </span>
                                    <strong>
                                      {Math.round((c.total / expense) * 100)}%
                                    </strong>
                                  </div>
                                ))}
                              </div>
                            </>
                          ) : (
                            <Empty title="Chưa có khoản chi">
                              Các danh mục chi tiêu sẽ được tổng hợp tại đây.
                            </Empty>
                          )}
                        </section>
                      </div>
                      <section className="panel">
                        <div className="panel-heading">
                          <div>
                            <h2>Giao dịch gần đây</h2>
                            <p className="muted">
                              Nhìn lại những khoản thu chi mới nhất
                            </p>
                          </div>
                          <button
                            className="text-button"
                            onClick={() => navigate("transactions")}
                          >
                            Xem tất cả
                            <ArrowRight size={16} />
                          </button>
                        </div>
                        <TransactionTable items={inRange.slice(0, 5)} />
                      </section>
                    </>
                  )}
                  {page === "transactions" && (
                    <section className="panel">
                      <div className="panel-heading">
                        <h2>
                          Lịch sử giao dịch{" "}
                          <span className="count">{filtered.length}</span>
                        </h2>
                      </div>
                      <div className="table-filters">
                        <label className="search-input">
                          <Search size={18} />
                          <input
                            aria-label="Tìm giao dịch"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder="Tìm ghi chú, danh mục…"
                          />
                        </label>
                        <select
                          aria-label="Lọc loại giao dịch"
                          value={kind}
                          onChange={(e) => setKind(e.target.value)}
                        >
                          <option value="ALL">Tất cả giao dịch</option>
                          <option value="INCOME">Thu nhập</option>
                          <option value="EXPENSE">Chi tiêu</option>
                        </select>
                        <span className="filter-note">
                          {filtered.length} giao dịch
                        </span>
                      </div>
                      <TransactionTable items={filtered} />
                    </section>
                  )}
                  {page === "categories" && (
                    <div className="category-layout">
                      <section className="panel">
                        <div className="panel-heading">
                          <div>
                            <h2>
                              Danh mục của bạn{" "}
                              <span className="count">{categories.length}</span>
                            </h2>
                            <p className="muted">
                              Mỗi khoản thu chi đều có một chỗ riêng
                            </p>
                          </div>
                        </div>
                        {categories.length ? (
                          <div className="category-grid">
                            {categories.map((c) => (
                              <article className="category-card" key={c.id}>
                                <span
                                  className={`transaction-icon ${c.type.toLowerCase()}`}
                                >
                                  {c.type === "INCOME" ? (
                                    <ArrowDownLeft size={21} />
                                  ) : (
                                    <FolderOpen size={21} />
                                  )}
                                </span>
                                <h3>{c.name}</h3>
                                <span
                                  className={`kind-label ${c.type.toLowerCase()}`}
                                >
                                  {c.type === "INCOME"
                                    ? "Thu nhập"
                                    : "Chi tiêu"}
                                </span>
                                <p>
                                  {
                                    transactions.filter(
                                      (t) => t.category?.id === c.id,
                                    ).length
                                  }{" "}
                                  giao dịch
                                </p>
                              </article>
                            ))}
                          </div>
                        ) : (
                          <Empty title="Tạo danh mục đầu tiên">
                            Bắt đầu với Ăn uống, Đi lại hoặc Lương để ghi chép
                            thuận tiện hơn.
                          </Empty>
                        )}
                      </section>
                      <CategoryForm
                        token={session.token}
                        onSaved={() => {
                          setSuccess("Đã thêm danh mục mới.");
                          void load();
                        }}
                      />
                    </div>
                  )}
                </>
              )}
            </>
          )}
          <footer className="page-footer">
            <span>Sổ nhỏ · Rõ ràng hôm nay, vững vàng ngày mai.</span>
            <span>
              <ShieldCheck size={14} />
              Dữ liệu riêng của bạn
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}
