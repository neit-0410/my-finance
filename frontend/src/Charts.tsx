import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import type { Transaction } from "./api";
import { Empty, money } from "./ui";
export function TransactionTable({ items }: { items: Transaction[] }) {
  return items.length ? (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Giao dịch</th>
            <th>Danh mục</th>
            <th>Ngày</th>
            <th className="align-right">Số tiền</th>
          </tr>
        </thead>
        <tbody>
          {items.map((t) => (
            <tr key={t.id}>
              <td>
                <div className="transaction-name">
                  <span className={`transaction-icon ${t.type.toLowerCase()}`}>
                    {t.type === "INCOME" ? (
                      <ArrowDownLeft size={19} />
                    ) : (
                      <ArrowUpRight size={19} />
                    )}
                  </span>
                  <div>
                    <strong>
                      {t.description || t.category?.name || "Giao dịch"}
                    </strong>
                    <span>{t.type === "INCOME" ? "Thu nhập" : "Chi tiêu"}</span>
                  </div>
                </div>
              </td>
              <td>
                <span className="tag">
                  {t.category?.name || "Chưa phân loại"}
                </span>
              </td>
              <td className="date-cell">
                {new Date(`${t.transactionDate}T00:00:00`).toLocaleDateString(
                  "vi-VN",
                )}
              </td>
              <td className={`amount ${t.type.toLowerCase()}`}>
                {t.type === "INCOME" ? "+" : "−"}
                {money(t.amount)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ) : (
    <Empty title="Chưa có giao dịch nào">
      Mỗi khoản thu chi là một mảnh ghép. Thêm giao dịch đầu tiên của bạn nhé.
    </Empty>
  );
}
export function Cashflow({
  items,
  start,
  end,
}: {
  items: Transaction[];
  start: string;
  end: string;
}) {
  // UTC day arithmetic avoids DST gaps while keeping date-only API values intact.
  const from = Date.parse(`${start}T00:00:00Z`),
    days = Math.max(
      1,
      Math.round((Date.parse(`${end}T00:00:00Z`) - from) / 86400000) + 1,
    );
  const count = Math.min(7, days);
  const buckets = Array.from({ length: count }, (_, i) => {
    const date = new Date(from + Math.floor((i * days) / count) * 86400000);
    const next = new Date(
      from + Math.floor(((i + 1) * days) / count) * 86400000,
    );
    const subset = items.filter(
      (t) =>
        t.transactionDate >= date.toISOString().slice(0, 10) &&
        t.transactionDate < next.toISOString().slice(0, 10),
    );
    return {
      label: `${date.getUTCDate()}/${date.getUTCMonth() + 1}`,
      income: subset
        .filter((t) => t.type === "INCOME")
        .reduce((s, t) => s + t.amount, 0),
      expense: subset
        .filter((t) => t.type === "EXPENSE")
        .reduce((s, t) => s + t.amount, 0),
    };
  });
  const max = Math.max(1, ...buckets.flatMap((b) => [b.income, b.expense]));
  return (
    <section className="panel cashflow">
      <div className="panel-heading">
        <div>
          <h2>Dòng tiền của bạn</h2>
          <p className="muted">Một góc nhìn rõ hơn về thu và chi</p>
        </div>
      </div>
      <div className="legend">
        <span>
          <i className="income-dot" />
          Thu nhập
        </span>
        <span>
          <i className="expense-dot" />
          Chi tiêu
        </span>
      </div>
      {!items.length ? (
        <Empty title="Dòng tiền đang chờ bạn">
          Biểu đồ sẽ xuất hiện khi có giao dịch trong khoảng thời gian đã chọn.
        </Empty>
      ) : (
        <div className="bar-chart" aria-label="Biểu đồ thu nhập và chi tiêu">
          <div className="chart-grid" aria-hidden="true">
            <span />
            <span />
            <span />
            <span />
          </div>
          {buckets.map((b, i) => (
            <div className="bar-column" key={i}>
              <div
                className="bar-pair"
                title={`${b.label}: Thu ${money(b.income)}, chi ${money(b.expense)}`}
              >
                <span
                  className="income-bar"
                  style={{ height: `${(b.income / max) * 100}%` }}
                />
                <span
                  className="expense-bar"
                  style={{ height: `${(b.expense / max) * 100}%` }}
                />
              </div>
              <span>{b.label}</span>
              <span className="sr-only">
                Thu {money(b.income)}, chi {money(b.expense)}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
