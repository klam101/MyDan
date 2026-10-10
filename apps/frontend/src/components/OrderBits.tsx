import { money } from "../domain/money";
import { describeModifier } from "../domain/modifiers";
import { linePrice } from "../domain/pricing";
import type { Order, OrderLine, OrderStatus } from "../domain/types";

export const STATUS_LABEL: Record<OrderStatus, string> = {
  open: "Cooking",
  ready: "Ready",
  picked_up: "Picked up",
  cancelled: "Cancelled",
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`status status-${status}`}>{STATUS_LABEL[status]}</span>;
}

export const clock = (iso: string, timezone: string) =>
  new Date(iso).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit", timeZone: timezone });

export const minutesSince = (iso: string, now = Date.now()) => Math.max(0, Math.floor((now - Date.parse(iso)) / 60_000));

export const customerLine = (order: Order) =>
  [order.type === "phone" ? "Phone" : "Walk-in", order.customerName, order.customerPhone].filter(Boolean).join(" · ");

/** A dish with its options, modifications and note, as the cashier and the kitchen read it. */
export function LineView({ line, showPrice, big }: { line: OrderLine; showPrice?: boolean; big?: boolean }) {
  return (
    <li className={big ? "line-view big" : "line-view"}>
      <div className="line-view-head">
        <span>
          <strong>{line.quantity} ×</strong> {line.code && <span className="line-code">{line.code}</span>}
          {line.name}
          {big && line.altName && <span className="item-alt"> {line.altName}</span>}
        </span>
        {showPrice && <span className="line-price">{money(linePrice(line))}</span>}
      </div>
      {line.options.length > 0 && <div className="line-options">{line.options.map((o) => o.name).join(" · ")}</div>}
      {line.modifiers.map((m) => (
        <div key={m.modifierId} className={`line-mod premod-${m.action}`}>
          {describeModifier(m)}
        </div>
      ))}
      {line.note && <div className="line-note">“{line.note}”</div>}
    </li>
  );
}

