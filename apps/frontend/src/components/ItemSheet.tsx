import { useMemo, useState } from "react";
import { buildLine } from "../domain/lines";
import { money } from "../domain/money";
import { actionLabel, describeModifier, listsForItem, PRE_MODIFIERS, togglePick } from "../domain/modifiers";
import { CHARGED_ACTIONS, linePrice } from "../domain/pricing";
import type { Menu, MenuCategory, MenuItem, NewOrderLine, PreModifier } from "../domain/types";
import { Sheet } from "./Sheet";

interface Props {
  menu: Menu;
  item: MenuItem;
  category: MenuCategory;
  /** The line being changed; absent when adding a new one. */
  initial?: NewOrderLine;
  onSave(line: NewOrderLine): void;
  onRemove?(): void;
  onClose(): void;
}

/**
 * Picks a dish's size and protein, and its modifications: choose NO / ADD / EXTRA / LIGHT /
 * ON SIDE / SUB, then tap ingredients. A dish with no modification lists goes into the order
 * as soon as its required picks are made, so a sized dish still takes two taps.
 */
export function ItemSheet({ menu, item, category, initial, onSave, onRemove, onClose }: Props) {
  const lists = useMemo(() => listsForItem(item, category, menu.modifierLists), [item, category, menu.modifierLists]);
  const actions = PRE_MODIFIERS.filter((p) => lists.some((l) => l.actions.includes(p.action)));
  const [optionIds, setOptionIds] = useState<string[]>(initial?.optionIds ?? []);
  const [modifiers, setModifiers] = useState(initial?.modifiers ?? []);
  const [action, setAction] = useState<PreModifier>(actions[0]?.action ?? "no");
  const [quantity, setQuantity] = useState(initial?.quantity ?? 1);
  const [note, setNote] = useState(initial?.note ?? "");

  const draft: NewOrderLine = { lineId: initial?.lineId, menuItemId: item.id, quantity, optionIds, modifiers, note };
  const missing = item.optionGroups.filter((g) => !g.options.some((o) => optionIds.includes(o.id)));
  let price: number | null = null;
  try {
    price = missing.length ? null : linePrice(buildLine(menu, draft, () => "preview"));
  } catch {
    price = null;
  }

  function pickOption(groupOptions: { id: string }[], id: string) {
    const next = [...optionIds.filter((o) => !groupOptions.some((g) => g.id === o)), id];
    setOptionIds(next);
    const complete = item.optionGroups.every((g) => g.options.some((o) => next.includes(o.id)));
    if (complete && !lists.length && !initial) onSave({ ...draft, optionIds: next });
  }

  const allModifiers = lists.flatMap((l) => l.modifiers);
  const pickedName = (id: string) => allModifiers.find((m) => m.id === id)?.name ?? "?";

  return (
    <Sheet
      wide={lists.length > 0}
      onClose={onClose}
      title={
        <>
          {item.code && <span className="item-code">{item.code}</span>} {item.name}
          {item.altName && <span className="sheet-alt"> {item.altName}</span>}
        </>
      }
    >
      {item.optionGroups.map((group) => (
        <section key={group.name} className="sheet-section">
          <h3>{group.name === "Choice" ? "Choose one" : group.name}</h3>
          <div className="options">
            {group.options.map((o) => (
              <button key={o.id} className={optionIds.includes(o.id) ? "option active" : "option"} onClick={() => pickOption(group.options, o.id)}>
                <span>{o.name}</span>
                {group.name === "Size" && <small>{money(item.priceCents + o.extraCents)}</small>}
              </button>
            ))}
          </div>
        </section>
      ))}

      {lists.length > 0 && (
        <section className="sheet-section">
          <h3>Modifications</h3>
          <div className="premods" role="radiogroup" aria-label="How to change the ingredient">
            {actions.map((a) => (
              <button
                key={a.action}
                role="radio"
                aria-checked={action === a.action}
                className={`premod premod-${a.action}${action === a.action ? " active" : ""}`}
                onClick={() => setAction(a.action)}
              >
                {a.label}
              </button>
            ))}
          </div>
          {lists.map((list) => {
            const allowed = list.actions.includes(action);
            return (
              <div key={list.id} className="mod-list">
                <h4>
                  {list.name}
                  {!allowed && <span className="muted"> · not with {actions.find((a) => a.action === action)?.label}</span>}
                </h4>
                <div className="mod-chips">
                  {list.modifiers.map((m) => {
                    const picked = modifiers.find((p) => p.modifierId === m.id);
                    const charge = CHARGED_ACTIONS.includes(action) && m.priceCents > 0;
                    return (
                      <button
                        key={m.id}
                        disabled={!allowed && !picked}
                        className={picked ? `mod-chip picked premod-${picked.action}` : "mod-chip"}
                        onClick={() => allowed && setModifiers(togglePick(modifiers, { action, modifierId: m.id }))}
                      >
                        {picked && <strong>{actionLabel(picked.action)} </strong>}
                        {m.name}
                        {charge && !picked && <small> +{money(m.priceCents)}</small>}
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}
          {modifiers.length > 0 && (
            <div className="picked-summary">
              {modifiers.map((p) => (
                <button key={p.modifierId} className={`picked-tag premod-${p.action}`} onClick={() => setModifiers(modifiers.filter((x) => x !== p))}>
                  {describeModifier({ action: p.action, name: pickedName(p.modifierId) })} <span aria-hidden>×</span>
                  <span className="sr-only">Remove</span>
                </button>
              ))}
            </div>
          )}
        </section>
      )}

      <section className="sheet-section sheet-extras">
        <input placeholder="Note for the kitchen (anything not listed above)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} />
        <div className="stepper" aria-label="Quantity">
          <button className="qty" onClick={() => setQuantity(Math.max(1, quantity - 1))} aria-label="One fewer">
            −
          </button>
          <span className="qty-count">{quantity}</span>
          <button className="qty" onClick={() => setQuantity(Math.min(99, quantity + 1))} aria-label="One more">
            +
          </button>
        </div>
      </section>

      <footer className="sheet-foot">
        {onRemove && (
          <button className="danger" onClick={onRemove}>
            Remove
          </button>
        )}
        <button className="primary" disabled={missing.length > 0 || price === null} onClick={() => onSave(draft)}>
          {missing.length
            ? `Pick ${missing.map((g) => (g.name === "Choice" ? "a choice" : g.name.toLowerCase())).join(" and ")}`
            : `${initial ? "Update" : "Add to order"} · ${money(price ?? 0)}`}
        </button>
      </footer>
    </Sheet>
  );
}
