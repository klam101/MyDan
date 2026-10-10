import { useEffect, useState } from "react";
import { Banner } from "../components/Banner";
import { clock, LineView, minutesSince } from "../components/OrderBits";
import { api } from "../data/client";
import { useAction, useLoad } from "../data/hooks";
import { useRestaurant } from "../data/session";
import type { Order } from "../domain/types";

/** Minutes after which an order turns amber, then red. */
const LATE = 15;
const VERY_LATE = 25;

/** The kitchen's view: open orders oldest first with how long each has waited. Ready clears it. */
export function KitchenScreen() {
  const { rid, restaurant } = useRestaurant();
  const { data: orders, error, reload } = useLoad(() => api.orders(rid), [rid], 5000);
  const [now, setNow] = useState(Date.now());
  const action = useAction();

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(timer);
  }, []);

  if (error) return <div className="splash">{error.message}</div>;
  if (!orders) return <div className="splash">Loading…</div>;

  const open = orders.filter((o) => o.status === "open").sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const recentlyReady = orders
    .filter((o) => o.status === "ready" && o.readyAt)
    .sort((a, b) => b.readyAt!.localeCompare(a.readyAt!))
    .slice(0, 4);

  async function setStatus(order: Order, status: "open" | "ready") {
    await action.run(() => api.setOrderStatus(rid, order.id, status));
    await reload();
  }

  return (
    <main className="page kitchen">
      <div className="page-head">
        <h1>Kitchen</h1>
        <span className="muted">
          {open.length} cooking · oldest first · amber after {LATE} min, red after {VERY_LATE}
        </span>
      </div>
      {action.error && <Banner kind="error">{action.error}</Banner>}

      <div className="kitchen-grid">
        {open.length === 0 && <p className="empty">All caught up</p>}
        {open.map((o) => {
          const waited = minutesSince(o.createdAt, now);
          const level = waited >= VERY_LATE ? "very-late" : waited >= LATE ? "late" : "";
          return (
            <article key={o.id} className={`kitchen-card ${level}`}>
              <header>
                <span className="order-number">#{o.number}</span>
                <span className="kitchen-type">{o.type === "phone" ? "PHONE" : "WALK-IN"}</span>
                {o.version > 1 && <span className="tag">CHANGED</span>}
                <span className="waited">{waited} min</span>
              </header>
              {(o.customerName || o.pickupAt) && (
                <div className="kitchen-who">
                  {o.customerName}
                  {o.pickupAt && <strong> · pickup {clock(o.pickupAt, restaurant.timezone)}</strong>}
                </div>
              )}
              <ul className="line-views">
                {o.lines.map((l) => (
                  <LineView key={l.id} line={l} big />
                ))}
              </ul>
              {o.note && <p className="order-note-view">Note: {o.note}</p>}
              <button className="primary ready-button" disabled={action.busy} onClick={() => setStatus(o, "ready")}>
                Ready
              </button>
            </article>
          );
        })}
      </div>

      {recentlyReady.length > 0 && (
        <div className="recent">
          <span className="muted">Just marked ready:</span>
          {recentlyReady.map((o) => (
            <button key={o.id} className="chip" disabled={action.busy} onClick={() => setStatus(o, "open")}>
              #{o.number} · undo
            </button>
          ))}
        </div>
      )}
    </main>
  );
}
