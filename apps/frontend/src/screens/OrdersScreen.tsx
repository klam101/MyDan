import { useState } from "react";
import { Link } from "react-router";
import { Banner } from "../components/Banner";
import { clock, customerLine, StatusBadge, STATUS_LABEL } from "../components/OrderBits";
import { api } from "../data/client";
import { useAction, useLoad } from "../data/hooks";
import { useRestaurant } from "../data/session";
import { money } from "../domain/money";
import type { Order, OrderStatus } from "../domain/types";

type Filter = "all" | OrderStatus;
const FILTERS: Filter[] = ["all", "open", "ready", "picked_up", "cancelled"];

/** The step that usually comes next for an order, offered right on its card. */
const NEXT: Partial<Record<OrderStatus, { status: "ready" | "picked_up"; label: string }>> = {
  open: { status: "ready", label: "Mark ready" },
  ready: { status: "picked_up", label: "Picked up" },
};

export function OrdersScreen() {
  const { rid, restaurant } = useRestaurant();
  const { data: orders, error, reload } = useLoad(() => api.orders(rid), [rid], 5000);
  const [filter, setFilter] = useState<Filter>("all");
  const [search, setSearch] = useState("");
  const action = useAction();

  if (error) return <div className="splash">{error.message}</div>;
  if (!orders) return <div className="splash">Loading…</div>;

  const q = search.trim().toLowerCase().replace(/^#/, "");
  const shown = orders.filter(
    (o) =>
      (filter === "all" || o.status === filter) &&
      (!q ||
        String(o.number) === q ||
        o.customerName?.toLowerCase().includes(q) ||
        o.customerPhone?.replace(/\D/g, "").includes(q.replace(/\D/g, "") || "\0")),
  );
  const counted = orders.filter((o) => o.status !== "cancelled");
  const count = (f: Filter) => (f === "all" ? orders.length : orders.filter((o) => o.status === f).length);

  async function advance(order: Order) {
    const next = NEXT[order.status];
    if (!next) return;
    await action.run(() => api.setOrderStatus(rid, order.id, next.status));
    await reload();
  }

  return (
    <main className="page">
      <div className="page-head">
        <h1>Today's orders</h1>
        <div className="day-stats">
          <span>
            <strong>{counted.length}</strong> orders
          </span>
          <span>
            <strong>{orders.filter((o) => o.status === "open").length}</strong> cooking
          </span>
          <span>
            <strong>{money(counted.reduce((s, o) => s + o.totalCents, 0))}</strong> sales incl. tax
          </span>
        </div>
      </div>
      <div className="toolbar">
        <div className="categories">
          {FILTERS.map((f) => (
            <button key={f} className={filter === f ? "chip active" : "chip"} onClick={() => setFilter(f)}>
              {f === "all" ? "All" : STATUS_LABEL[f]} <span className="count">{count(f)}</span>
            </button>
          ))}
        </div>
        <input type="search" placeholder="Order #, name or phone" value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>
      {action.error && <Banner kind="error">{action.error}</Banner>}

      <div className="order-cards">
        {shown.length === 0 && <p className="empty">No orders here yet</p>}
        {shown.map((o) => {
          const job = o.printJobs.at(-1);
          const next = NEXT[o.status];
          return (
            <article key={o.id} className={o.status === "cancelled" ? "order-card cancelled" : "order-card"}>
              <Link to={`/r/${rid}/orders/${o.id}`} className="order-card-link">
                <header>
                  <span className="order-number">#{o.number}</span>
                  <StatusBadge status={o.status} />
                  {o.version > 1 && <span className="tag">Changed</span>}
                  {job?.status === "failed" && <span className="tag tag-error">Ticket failed</span>}
                  <span className="right">{clock(o.createdAt, restaurant.timezone)}</span>
                </header>
                <div className="muted">
                  {customerLine(o)}
                  {o.pickupAt && ` · pickup ${clock(o.pickupAt, restaurant.timezone)}`}
                </div>
                <div className="order-card-items">
                  {o.lines.map((l) => `${l.quantity}× ${l.code || l.name}`).join("  ")}
                </div>
              </Link>
              <footer>
                <strong>{money(o.totalCents)}</strong>
                {next && (
                  <button className="secondary small" disabled={action.busy} onClick={() => advance(o)}>
                    {next.label}
                  </button>
                )}
              </footer>
            </article>
          );
        })}
      </div>
    </main>
  );
}
