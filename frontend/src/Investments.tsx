import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Check,
  CircleDollarSign,
  LineChart,
  LoaderCircle,
  Plus,
  RefreshCw,
  TrendingUp,
} from "lucide-react";
import { api } from "./api";
import type { Session } from "./api";
import { Alert, Button, Empty, errorMessage, money, today } from "./ui";
interface Position {
  symbol: string;
  quantity: number;
  averageCost: number;
  costBasis: number;
  currentPrice: number | null;
  priceUpdatedAt: string | null;
  marketValue: number | null;
  unrealizedPnl: number | null;
  realizedPnl: number;
}
interface Trade {
  id: number;
  symbol: string;
  side: "BUY" | "SELL";
  quantity: number;
  price: number;
  fee: number;
  tradeDate: string;
  realizedPnl: number;
}
interface Portfolio {
  costBasis: number;
  marketValue: number | null;
  unrealizedPnl: number | null;
  realizedPnl: number;
  missingPrices: number;
  positions: Position[];
  trades: Trade[];
}
const priceFormat = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 4,
});
const value = (amount: number | null) =>
  amount === null ? "Chưa có giá" : money(amount);
const pnlClass = (amount: number | null) =>
  amount === null ? "muted" : amount < 0 ? "stock-loss" : "income";

export default function Investments({ session }: { session: Session }) {
  const [data, setData] = useState<Portfolio | null>(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const [tab, setTab] = useState<"positions" | "trades">("positions"),
    [success, setSuccess] = useState("");
  const [side, setSide] = useState<"BUY" | "SELL">("BUY"),
    [busy, setBusy] = useState(false),
    [tradeError, setTradeError] = useState("");
  const [quoteSymbol, setQuoteSymbol] = useState(""),
    [quoteBusy, setQuoteBusy] = useState(false),
    [quoteError, setQuoteError] = useState("");
  const load = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setError("");
      try {
        const result = await api<Portfolio>("/investments", {
          token: session.token,
          signal,
        });
        if (!signal?.aborted) setData(result);
      } catch (err) {
        if (!signal?.aborted) setError(errorMessage(err));
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [session.token],
  );
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);
  useEffect(() => {
    if (!success) return;
    const timer = setTimeout(() => setSuccess(""), 6000);
    return () => clearTimeout(timer);
  }, [success]);
  async function trade(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget,
      input = new FormData(form);
    setBusy(true);
    setTradeError("");
    setSuccess("");
    try {
      const result = await api<Portfolio>("/investments/trades", {
        token: session.token,
        body: {
          symbol: String(input.get("symbol")).trim().toUpperCase(),
          side,
          quantity: Number(input.get("quantity")),
          price: String(input.get("price")),
          fee: String(input.get("fee")),
          tradeDate: input.get("tradeDate"),
        },
      });
      setData(result);
      form.reset();
      setSuccess("Đã ghi nhận giao dịch cổ phiếu.");
    } catch (err) {
      setTradeError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }
  async function quote(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget,
      input = new FormData(form);
    setQuoteBusy(true);
    setQuoteError("");
    setSuccess("");
    try {
      const result = await api<Portfolio>("/investments/prices", {
        token: session.token,
        body: { symbol: quoteSymbol, price: String(input.get("quotePrice")) },
      });
      setData(result);
      form.reset();
      setSuccess("Đã cập nhật giá tham chiếu nhập tay.");
    } catch (err) {
      setQuoteError(errorMessage(err));
    } finally {
      setQuoteBusy(false);
    }
  }
  if (loading)
    return (
      <div className="loading" role="status">
        <LoaderCircle className="spin" />
        Đang tải danh mục đầu tư…
      </div>
    );
  if (error || !data)
    return (
      <div className="error-row">
        <Alert>{error || "Chưa thể tải danh mục đầu tư."}</Alert>
        <button className="secondary" onClick={() => void load()}>
          Thử lại
        </button>
      </div>
    );
  return (
    <div className="investment-module">
      <div className="investment-notice">
        <LineChart size={19} />
        <div>
          <strong>Đầu tư có ghi chép, quyết định có cơ sở.</strong>
          <p>
            Giá tham chiếu do bạn nhập thủ công, không phải giá trực tiếp từ
            sàn. Đơn vị: VND/cổ phiếu.
          </p>
        </div>
      </div>
      {success && (
        <div className="toast" role="status">
          <Check size={17} />
          {success}
        </div>
      )}
      <div className="investment-stats">
        <section className="stat-card balance">
          <div>
            <span>Vốn đang nắm giữ</span>
            <CircleDollarSign size={20} />
          </div>
          <strong>{money(data.costBasis)}</strong>
          <p>Giá vốn bình quân, gồm phí mua</p>
        </section>
        <section className="stat-card">
          <div>
            <span>Giá trị danh mục</span>
            <TrendingUp size={20} />
          </div>
          <strong>{value(data.marketValue)}</strong>
          <p>
            {data.missingPrices
              ? `${data.missingPrices} mã đang thiếu giá tham chiếu`
              : "Theo giá tham chiếu bạn đã nhập"}
          </p>
        </section>
        <section className="stat-card">
          <div>
            <span>Lãi/lỗ chưa chốt</span>
            <LineChart size={20} />
          </div>
          <strong className={pnlClass(data.unrealizedPnl)}>
            {value(data.unrealizedPnl)}
          </strong>
          <p>Giá trị danh mục trừ vốn nắm giữ</p>
        </section>
        <section className="stat-card">
          <div>
            <span>Lãi/lỗ đã chốt</span>
            <Check size={20} />
          </div>
          <strong className={pnlClass(data.realizedPnl)}>
            {money(data.realizedPnl)}
          </strong>
          <p>Đã trừ giá vốn và phí bán</p>
        </section>
      </div>
      <div className="investment-content">
        <div className="investment-ledger">
          <section className="panel">
            <div className="panel-heading">
              <h2>Danh mục chứng khoán</h2>
              <button
                className="icon-button"
                aria-label="Tải lại đầu tư"
                disabled={busy || quoteBusy}
                onClick={() => void load()}
              >
                <RefreshCw size={17} />
              </button>
            </div>
            <div
              className="investment-tabs"
              role="tablist"
              aria-label="Dữ liệu đầu tư"
            >
              <button
                role="tab"
                id="positions-tab"
                aria-controls="stock-panel"
                aria-selected={tab === "positions"}
                onClick={() => setTab("positions")}
              >
                Nắm giữ{" "}
                <span className="count">
                  {data.positions.filter((p) => p.quantity > 0).length}
                </span>
              </button>
              <button
                role="tab"
                id="trades-tab"
                aria-controls="stock-panel"
                aria-selected={tab === "trades"}
                onClick={() => setTab("trades")}
              >
                Lịch sử mua/bán{" "}
                <span className="count">{data.trades.length}</span>
              </button>
            </div>
            <div
              role="tabpanel"
              id="stock-panel"
              aria-labelledby={
                tab === "positions" ? "positions-tab" : "trades-tab"
              }
            >
              {tab === "positions" ? (
                data.positions.length ? (
                  <div className="table-scroll stock-table">
                    <table>
                      <thead>
                        <tr>
                          <th>Mã / Số lượng</th>
                          <th>Giá vốn/CP</th>
                          <th>Giá tham chiếu/CP</th>
                          <th className="align-right">Giá trị</th>
                          <th className="align-right">Lãi/lỗ chưa chốt</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.positions.map((p) => (
                          <tr key={p.symbol}>
                            <td>
                              <strong className="ticker">{p.symbol}</strong>
                              <span className="stock-detail">
                                {p.quantity
                                  ? `${p.quantity.toLocaleString("vi-VN")} cổ phiếu`
                                  : "Đã bán hết"}
                              </span>
                            </td>
                            <td>{priceFormat.format(p.averageCost)}</td>
                            <td>
                              <button
                                className="text-button"
                                onClick={() => {
                                  setQuoteSymbol(p.symbol);
                                  document
                                    .getElementById("quote-price")
                                    ?.focus();
                                }}
                              >
                                {p.currentPrice === null
                                  ? "Nhập giá"
                                  : priceFormat.format(p.currentPrice)}
                              </button>
                              <span className="stock-detail">
                                {p.priceUpdatedAt
                                  ? new Date(p.priceUpdatedAt).toLocaleString(
                                      "vi-VN",
                                    )
                                  : "Chưa cập nhật"}
                              </span>
                            </td>
                            <td className="align-right">
                              {value(p.marketValue)}
                            </td>
                            <td
                              className={`amount ${pnlClass(p.unrealizedPnl)}`}
                            >
                              {value(p.unrealizedPnl)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <Empty title="Danh mục đầu tư đang trống">
                    Ghi nhận lần mua cổ phiếu đầu tiên bằng biểu mẫu bên dưới.
                  </Empty>
                )
              ) : data.trades.length ? (
                <div className="table-scroll stock-table">
                  <table>
                    <thead>
                      <tr>
                        <th>Ngày / Mã</th>
                        <th>Loại</th>
                        <th>Số lượng</th>
                        <th>Giá/CP</th>
                        <th>Phí</th>
                        <th className="align-right">Lãi/lỗ đã chốt</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.trades.map((t) => (
                        <tr key={t.id}>
                          <td>
                            <strong className="ticker">{t.symbol}</strong>
                            <span className="stock-detail">
                              {new Date(
                                `${t.tradeDate}T00:00:00`,
                              ).toLocaleDateString("vi-VN")}
                            </span>
                          </td>
                          <td>
                            <span
                              className={`kind-label ${t.side === "BUY" ? "income" : "expense"}`}
                            >
                              {t.side === "BUY" ? "Mua" : "Bán"}
                            </span>
                          </td>
                          <td>{t.quantity.toLocaleString("vi-VN")}</td>
                          <td>{priceFormat.format(t.price)}</td>
                          <td>{priceFormat.format(t.fee)}</td>
                          <td className={`amount ${pnlClass(t.realizedPnl)}`}>
                            {t.side === "SELL" ? money(t.realizedPnl) : "—"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty title="Chưa có lịch sử mua/bán">
                  Các giao dịch đã lưu sẽ hiển thị tại đây.
                </Empty>
              )}
            </div>
          </section>
          <section className="panel stock-trade-form">
            <div className="panel-heading">
              <h2>Ghi nhận mua / bán</h2>
              <Plus size={18} />
            </div>
            {tradeError && <Alert>{tradeError}</Alert>}
            <form
              className="form-grid"
              onSubmit={trade}
              aria-label="Giao dịch chứng khoán"
            >
              <label>
                Loại lệnh
                <select
                  value={side}
                  onChange={(e) => setSide(e.target.value as "BUY" | "SELL")}
                >
                  <option value="BUY">Mua cổ phiếu</option>
                  <option value="SELL">Bán cổ phiếu</option>
                </select>
              </label>
              <label>
                Mã cổ phiếu
                <input
                  name="symbol"
                  required
                  pattern="[A-Za-z][A-Za-z0-9.\-]{0,11}"
                  maxLength={12}
                  placeholder="Ví dụ: FPT"
                  className="ticker-input"
                />
              </label>
              <label>
                Số lượng cổ phiếu
                <input
                  name="quantity"
                  type="number"
                  required
                  min="1"
                  max="1000000000"
                  step="1"
                  placeholder="100"
                />
              </label>
              <label>
                Giá mỗi cổ phiếu (VND)
                <input
                  name="price"
                  type="number"
                  required
                  min="0.0001"
                  max="999999999999.9999"
                  step="0.0001"
                  placeholder="Ví dụ: 95000"
                />
              </label>
              <label>
                Phí giao dịch (VND)
                <input
                  name="fee"
                  type="number"
                  required
                  min="0"
                  max="999999999999.9999"
                  step="0.0001"
                  defaultValue="0"
                />
              </label>
              <label>
                Ngày khớp lệnh
                <input
                  name="tradeDate"
                  type="date"
                  required
                  defaultValue={today()}
                  max={today()}
                />
              </label>
              <p className="form-hint wide">
                Ghi nhận giao dịch đã thực hiện. Không đặt lệnh lên sàn, không
                tự động ghi thu chi cá nhân.
              </p>
              <div className="form-actions wide">
                <Button className="primary" busy={busy} disabled={quoteBusy}>
                  {side === "BUY" ? (
                    <ArrowDownLeft size={17} />
                  ) : (
                    <ArrowUpRight size={17} />
                  )}
                  Lưu giao dịch cổ phiếu
                </Button>
              </div>
            </form>
          </section>
        </div>
        <aside className="panel stock-quote-form">
          <div className="panel-heading">
            <h2>Giá tham chiếu</h2>
            <TrendingUp size={19} />
          </div>
          <p className="muted">
            Cập nhật giá bạn muốn dùng để định giá danh mục. Nhập giá đầy đủ
            bằng đồng, ví dụ 95.000đ thì nhập 95000.
          </p>
          {quoteError && <Alert>{quoteError}</Alert>}
          <form
            className="stack"
            onSubmit={quote}
            aria-label="Cập nhật giá cổ phiếu"
          >
            <label>
              Mã cần cập nhật
              <select
                required
                value={quoteSymbol}
                onChange={(e) => setQuoteSymbol(e.target.value)}
              >
                <option value="" disabled>
                  Chọn mã cổ phiếu
                </option>
                {data.positions.map((p) => (
                  <option key={p.symbol} value={p.symbol}>
                    {p.symbol}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Giá tham chiếu (VND/CP)
              <input
                id="quote-price"
                name="quotePrice"
                type="number"
                required
                min="0.0001"
                max="999999999999.9999"
                step="0.0001"
                placeholder="Nhập giá hiện tại"
              />
            </label>
            <Button
              className="primary"
              busy={quoteBusy}
              disabled={!data.positions.length || busy}
            >
              <Check size={17} />
              Cập nhật giá
            </Button>
          </form>
          <div className="stock-method">
            <strong>Cách tính lãi/lỗ</strong>
            <p>
              Phí mua cộng vào giá vốn bình quân. Khi bán, lãi/lỗ = tiền bán −
              phí bán − giá vốn phần đã bán.
            </p>
            <p>
              Không có giá tham chiếu: chưa tính tổng giá trị và lãi/lỗ chưa
              chốt. Chưa tính cổ tức, chia tách hoặc thuế riêng ngoài phí bạn
              nhập.
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
}
