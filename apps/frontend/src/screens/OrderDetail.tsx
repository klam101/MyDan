import { useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router";
import { Banner } from "../components/Banner";
import { clock, customerLine, LineView, StatusBadge } from "../components/OrderBits";
import { PRINT_LABEL } from "../components/PrintStatus";
import { TicketPreview } from "../components/TicketPreview";
import { api } from "../data/client";
import { useAction, useLoad } from "../data/hooks";
import { useRestaurant } from "../data/session";
import { money } from "../domain/money";
import type { Order, PrintJob } from "../domain/types";

const KIND_LABEL: Record<PrintJob["kind"], string> = {
  new: "New ticket",
  changed: "CHANGED ticket",
  cancelled: "CANCELLED ticket",
  reprint: "Reprint",
  test: "Test",
};

export function OrderDetail() {
  const { rid, restaurant, can } = useRestaurant();
  const { orderId = "" } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { data: order, error, setData } = useLoad(() => api.order(rid, orderId), [rid, orderId], 2000);
  const [jobId, setJobId] = useState<string | null>(null);
  const action = useAction();
  const [notice, setNotice] = useState<string | null>((location.state as { changed?: boolean } | null)?.changed ? "Changes saved. A CHANGED ticket went to the kitchen." : null);

  if (error) return <div className="splash">{error.message}</div>;
  if (!order) return <div className="splash">Loading…</div>;

  const job = order.printJobs.find((j) => j.id === jobId) ?? order.printJobs.at(-1);
  const closed = order.status === "cancelled" || order.status === "picked_up";

  async function act(fn: () => Promise<Order>, message: string) {
    const updated = await action.run(fn);
    if (updated) {
      setData(updated);
      setJobId(null);
      setNotice(message);
    }
  }

  return (
    <main className="page detail">
      <section className="detail-main">
        <Link className="link back" to={`/r/${rid}/orders`}>
          ‹ Orders
        </Link>
        <div className="detail-head">
          <h1>Order #{order.number}</h1>
          <StatusBadge status={order.status} />
          {order.version > 1 && <span className="tag">Changed {order.version - 1}×</span>}
        </div>
        <p className="muted">
          {customerLine(order)} · taken {clock(order.createdAt, restaurant.timezone)} by {order.createdBy.name}
          {order.pickupAt && ` · pickup ${clock(order.pickupAt, restaurant.timezone)}`}
          {order.readyAt && ` · ready ${clock(order.readyAt, restaurant.timezone)}`}
        </p>
        {notice && (
          <Banner kind="ok" onClose={() => setNotice(null)}>
            {notice}
          </Banner>
        )}
        {action.error && <Banner kind="error">{action.error}</Banner>}

        <ul className="line-views">
          {order.lines.map((l) => (
            <LineView key={l.id} line={l} showPrice />
          ))}
        </ul>
        {order.note && <p className="order-note-view">Note: {order.note}</p>}
        <dl className="totals">
          <dt>Subtotal</dt>
          <dd>{money(order.subtotalCents)}</dd>
          <dt>Tax</dt>
          <dd>{money(order.taxCents)}</dd>
          <dt className="grand">Total</dt>
          <dd className="grand">{money(order.totalCents)}</dd>
        </dl>

        <div className="detail-actions">
          {order.status === "open" && (
            <button className="primary" disabled={action.busy} onClick={() => act(() => api.setOrderStatus(rid, order.id, "ready"), "Marked ready.")}>
              Mark ready
            </button>
          )}
          {order.status === "ready" && (
            <>
              <button className="primary" disabled={action.busy} onClick={() => act(() => api.setOrderStatus(rid, order.id, "picked_up"), "Picked up.")}>
                Picked up
              </button>
              <button className="secondary" disabled={action.busy} onClick={() => act(() => api.setOrderStatus(rid, order.id, "open"), "Back to cooking.")}>
                Not ready yet
              </button>
            </>
          )}
          {!closed && can("order") && (
            <button className="secondary" onClick={() => navigate(`/r/${rid}/orders/${order.id}/edit`)}>
              Change order
            </button>
          )}
          <button className="secondary" disabled={action.busy} onClick={() => act(() => api.reprint(rid, order.id), "Ticket sent to the printer again.")}>
            Reprint ticket
          </button>
          {!closed && can("order") && (
            <button
              className="danger"
              disabled={action.busy}
              onClick={() => confirm(`Cancel order #${order.number}? A CANCELLED ticket will print.`) && act(() => api.cancelOrder(rid, order.id), "Order cancelled. A CANCELLED ticket went to the kitchen.")}
            >
              Cancel order
            </button>
          )}
        </div>
      </section>

      <aside className="detail-side">
        <h2>Kitchen tickets</h2>
        <ul className="jobs">
          {order.printJobs.map((j) => (
            <li key={j.id}>
              <button className={j.id === job?.id ? "job active" : "job"} onClick={() => setJobId(j.id)}>
                <span>{KIND_LABEL[j.kind]}</span>
                <span className="muted">{clock(j.createdAt, restaurant.timezone)}</span>
                <span className={`print-${j.status}`}>{PRINT_LABEL[j.status]}</span>
              </button>
            </li>
          ))}
        </ul>
        {job && <TicketPreview ticket={job.ticket} />}
      </aside>
    </main>
  );
}
