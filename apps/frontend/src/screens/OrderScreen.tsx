import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { Banner } from "../components/Banner";
import { ItemSheet } from "../components/ItemSheet";
import { SentBanner } from "../components/PrintStatus";
import { api } from "../data/client";
import { errorMessage, useLoad } from "../data/hooks";
import { useRestaurant } from "../data/session";
import { buildLine, lineInput } from "../domain/lines";
import { money } from "../domain/money";
import { describeModifier, findItem } from "../domain/modifiers";
import { linePrice, priceLabel, totals } from "../domain/pricing";
import type { Menu, MenuItem, NewOrderLine, Order, OrderLine, OrderType } from "../domain/types";

interface CartLine {
  key: number;
  input: NewOrderLine;
}

let nextKey = 1;

/** The line as it will be saved, or why it can't be (e.g. the dish sold out meanwhile). */
function preview(menu: Menu, input: NewOrderLine): { line?: OrderLine; error?: string } {
  try {
    return { line: buildLine(menu, input, () => "preview") };
  } catch (e) {
    return { error: errorMessage(e) };
  }
}

const sameLine = (a: NewOrderLine, b: NewOrderLine) =>
  a.menuItemId === b.menuItemId &&
  !a.note &&
  !b.note &&
  JSON.stringify([[...a.optionIds].sort(), a.modifiers]) === JSON.stringify([[...b.optionIds].sort(), b.modifiers]);

/** Pickup choices every 5 minutes for the next two hours, labelled in the restaurant's time. */
function pickupChoices(timezone: string, current: string | null): { value: string; label: string }[] {
  const now = Date.now();
  const start = Math.ceil((now + 10 * 60_000) / (5 * 60_000)) * 5 * 60_000;
  const fmt = (ms: number) => new Date(ms).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit", timeZone: timezone });
  const choices = Array.from({ length: 23 }, (_, i) => {
    const at = start + i * 5 * 60_000;
    return { value: new Date(at).toISOString(), label: `${fmt(at)} (in ${Math.round((at - now) / 60_000)} min)` };
  });
  if (current && !choices.some((c) => c.value === current)) choices.unshift({ value: current, label: fmt(Date.parse(current)) });
  return choices;
}

export function OrderScreen() {
  const { rid, restaurant } = useRestaurant();
  const { orderId } = useParams();
  const navigate = useNavigate();
  const { data: menu, error: menuError } = useLoad(() => api.menu(rid), [rid]);
  const { data: editing, error: orderError } = useLoad(() => (orderId ? api.order(rid, orderId) : Promise.resolve(null)), [rid, orderId]);

  const [categoryId, setCategoryId] = useState<string>();
  const [search, setSearch] = useState("");
  const [cart, setCart] = useState<CartLine[]>([]);
  const [type, setType] = useState<OrderType>("walk_in");
  const [customerName, setCustomerName] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [pickupAt, setPickupAt] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [sheet, setSheet] = useState<{ item: MenuItem; key?: number } | null>(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState<Order | null>(null);
  const [lastAdded, setLastAdded] = useState<number | null>(null);

  // Editing a sent order: load it into the cart once.
  useEffect(() => {
    if (!editing) return;
    setCart(editing.lines.map((l) => ({ key: nextKey++, input: lineInput(l) })));
    setType(editing.type);
    setCustomerName(editing.customerName ?? "");
    setCustomerPhone(editing.customerPhone ?? "");
    setPickupAt(editing.pickupAt);
    setNote(editing.note ?? "");
  }, [editing]);

  const previews = useMemo(() => (menu ? cart.map((c) => ({ ...c, ...preview(menu, c.input) })) : []), [menu, cart]);
  const valid = previews.flatMap((p) => (p.line ? [p.line] : []));
  const sum = totals(valid, restaurant.taxRate);
  const blocked = previews.some((p) => p.error);

  if (menuError || orderError) return <div className="splash">{(menuError ?? orderError)!.message}</div>;
  if (!menu || (orderId && !editing)) return <div className="splash">Loading…</div>;
  if (editing && (editing.status === "cancelled" || editing.status === "picked_up")) {
    return <div className="splash">Order #{editing.number} is closed and can't be changed.</div>;
  }

  const category = menu.categories.find((c) => c.id === categoryId) ?? menu.categories[0];
  const query = search.trim().toLowerCase();
  const shownItems = query
    ? menu.categories.flatMap((c) => c.items).filter((i) => i.code.toLowerCase() === query || (query.length > 1 && i.name.toLowerCase().includes(query)))
    : (category?.items ?? []);

  function addLine(input: NewOrderLine) {
    setSearch("");
    setSheet(null);
    setError(null);
    setCart((lines) => {
      const same = lines.find((l) => sameLine(l.input, input));
      if (same) {
        setLastAdded(same.key);
        return lines.map((l) => (l === same ? { ...l, input: { ...l.input, quantity: Math.min(99, l.input.quantity + input.quantity) } } : l));
      }
      const key = nextKey++;
      setLastAdded(key);
      return [...lines, { key, input }];
    });
  }

  function tap(item: MenuItem) {
    setSent(null);
    if (item.optionGroups.length) setSheet({ item });
    else addLine({ menuItemId: item.id, quantity: 1, optionIds: [], modifiers: [] });
  }

  const updateLine = (key: number, input: NewOrderLine | null) => {
    setSheet(null);
    setCart((lines) => (input ? lines.map((l) => (l.key === key ? { ...l, input } : l)) : lines.filter((l) => l.key !== key)));
  };

  const changeQty = (key: number, delta: number) =>
    setCart((lines) =>
      lines.flatMap((l) => {
        if (l.key !== key) return [l];
        const quantity = l.input.quantity + delta;
        return quantity <= 0 ? [] : [{ ...l, input: { ...l.input, quantity: Math.min(99, quantity) } }];
      }),
    );

  function clear() {
    setCart([]);
    setType("walk_in");
    setCustomerName("");
    setCustomerPhone("");
    setPickupAt(null);
    setNote("");
    setError(null);
  }

  async function send() {
    setSending(true);
    setError(null);
    const input = { type, customerName, customerPhone, pickupAt: type === "phone" ? pickupAt : null, note, lines: cart.map((c) => c.input) };
    try {
      if (editing) {
        const saved = await api.updateOrder(rid, editing.id, input);
        navigate(`/r/${rid}/orders/${saved.id}`, { state: { changed: true } });
        return;
      }
      setSent(await api.createOrder(rid, input));
      clear();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSending(false);
    }
  }

  const sheetTarget = sheet && findItem(menu, sheet.item.id);
  const sheetLine = sheet?.key !== undefined ? cart.find((c) => c.key === sheet.key) : undefined;

  return (
    <main className="order-screen">
      <section className="menu-pane">
        <input
          className="search"
          type="search"
          placeholder="Menu number or name, e.g. L6 or lo mein"
          autoCapitalize="characters"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {!query && (
          <div className="categories">
            {menu.categories.map((c) => (
              <button key={c.id} className={c.id === category?.id ? "chip active" : "chip"} onClick={() => setCategoryId(c.id)}>
                {c.name}
              </button>
            ))}
          </div>
        )}
        {!query && category?.note && <p className="category-note">{category.note}</p>}
        <div className="items">
          {query && shownItems.length === 0 && <p className="empty">No dish matches “{search}”</p>}
          {shownItems.map((item) => (
            <button key={item.id} className="item" disabled={!item.available} onClick={() => tap(item)}>
              <span className="item-head">
                {item.code && <span className="item-code">{item.code}</span>}
                {item.spicy && (
                  <span className="spicy" title="Hot & spicy">
                    ★
                  </span>
                )}
              </span>
              <span className="item-name">{item.name}</span>
              {item.altName && <span className="item-alt">{item.altName}</span>}
              <span className="item-price">{item.available ? priceLabel(item, money) : "Sold out"}</span>
            </button>
          ))}
        </div>
      </section>

      <aside className="cart">
        {editing && <div className="editing-tag">Changing order #{editing.number} · a CHANGED ticket will print</div>}
        <div className="segmented">
          <button className={type === "walk_in" ? "active" : ""} onClick={() => setType("walk_in")}>
            Walk-in
          </button>
          <button className={type === "phone" ? "active" : ""} onClick={() => setType("phone")}>
            Phone
          </button>
        </div>
        <div className="customer">
          <input placeholder="Name" value={customerName} onChange={(e) => setCustomerName(e.target.value)} />
          <input placeholder="Phone" inputMode="tel" value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} />
          {type === "phone" && (
            <select className="pickup" value={pickupAt ?? ""} onChange={(e) => setPickupAt(e.target.value || null)} aria-label="Pickup time">
              <option value="">Pickup: as soon as ready</option>
              {pickupChoices(restaurant.timezone, pickupAt).map((c) => (
                <option key={c.value} value={c.value}>
                  Pickup {c.label}
                </option>
              ))}
            </select>
          )}
        </div>

        <ul className="lines">
          {cart.length === 0 && <li className="empty">Tap dishes to add them</li>}
          {previews.map((p) => (
            <li key={p.key} className={p.key === lastAdded ? "line just-added" : "line"}>
              <div className="line-main">
                <button className="qty" onClick={() => changeQty(p.key, -1)} aria-label="One fewer">
                  −
                </button>
                <span className="qty-count">{p.input.quantity}</span>
                <button className="qty" onClick={() => changeQty(p.key, 1)} aria-label="One more">
                  +
                </button>
                <div className="line-name">
                  <span>
                    {p.line?.code && <span className="line-code">{p.line.code}</span>}
                    {p.line?.name ?? "Unavailable dish"}
                  </span>
                  {p.line && p.line.options.length > 0 && <span className="line-options">{p.line.options.map((o) => o.name).join(" · ")}</span>}
                  {p.line?.modifiers.map((m) => (
                    <span key={m.modifierId} className={`line-mod premod-${m.action}`}>
                      {describeModifier(m)}
                      {m.priceCents > 0 && ` +${money(m.priceCents)}`}
                    </span>
                  ))}
                  {p.line?.note && <span className="line-note">“{p.line.note}”</span>}
                  {p.error && <span className="line-error">{p.error}</span>}
                </div>
                <span className="line-price">{p.line ? money(linePrice(p.line)) : ""}</span>
              </div>
              {p.line && (
                <button className="modify" onClick={() => setSheet({ item: findItem(menu, p.input.menuItemId)!.item, key: p.key })}>
                  Modify dish ›
                </button>
              )}
              {!p.line && (
                <button className="modify" onClick={() => updateLine(p.key, null)}>
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>

        <input className="order-note" placeholder="Note for the whole order" value={note} onChange={(e) => setNote(e.target.value)} maxLength={300} />
        <dl className="totals">
          <dt>Subtotal</dt>
          <dd>{money(sum.subtotalCents)}</dd>
          <dt>Tax</dt>
          <dd>{money(sum.taxCents)}</dd>
          <dt className="grand">Total</dt>
          <dd className="grand">{money(sum.totalCents)}</dd>
        </dl>
        {error && <Banner kind="error">{error}</Banner>}
        {sent && <SentBanner rid={rid} order={sent} onClose={() => setSent(null)} />}
        <div className="actions">
          {editing ? (
            <button className="secondary" onClick={() => navigate(`/r/${rid}/orders/${editing.id}`)} disabled={sending}>
              Back
            </button>
          ) : (
            <button className="secondary" onClick={clear} disabled={sending || cart.length === 0}>
              Clear
            </button>
          )}
          <button className="primary" onClick={send} disabled={sending || cart.length === 0 || blocked}>
            {sending ? "Sending…" : editing ? "Save & print CHANGED" : "Send to kitchen"}
          </button>
        </div>
      </aside>

      {sheet && sheetTarget && (
        <ItemSheet
          menu={menu}
          item={sheetTarget.item}
          category={sheetTarget.category}
          initial={sheetLine?.input}
          onClose={() => setSheet(null)}
          onSave={(input) => (sheetLine ? updateLine(sheetLine.key, input) : addLine(input))}
          onRemove={sheetLine ? () => updateLine(sheetLine.key, null) : undefined}
        />
      )}
    </main>
  );
}
