import { useState } from "react";
import { Banner } from "../components/Banner";
import { MenuTabs } from "../components/MenuTabs";
import { MoneyInput } from "../components/MoneyInput";
import { Sheet } from "../components/Sheet";
import type { ModifierListInput } from "../data/api";
import { api } from "../data/client";
import { useAction, useLoad } from "../data/hooks";
import { useRestaurant } from "../data/session";
import { money } from "../domain/money";
import { actionLabel, PRE_MODIFIERS } from "../domain/modifiers";
import { CHARGED_ACTIONS } from "../domain/pricing";
import type { Menu, ModifierList, PreModifier } from "../domain/types";

/** Shared lists of modifications (Vegetables, Sauce & spice…), made once and attached to categories or dishes. */
export function ModifiersScreen() {
  const { rid } = useRestaurant();
  const { data: menu, error, reload } = useLoad(() => api.menu(rid), [rid]);
  const [editing, setEditing] = useState<{ list?: ModifierList } | null>(null);

  if (error) return <div className="splash">{error.message}</div>;
  if (!menu) return <div className="splash">Loading…</div>;

  return (
    <main className="page">
      <MenuTabs />
      <div className="page-head">
        <p className="muted">
          A list lets the cashier change a dish: pick NO, ADD, MORE, LESS, ON SIDE, CHANGE or ONLY, then the ingredient. Attach lists to a whole category on the Dishes tab, or to single dishes.
        </p>
        <button className="secondary" onClick={() => setEditing({})}>
          + New list
        </button>
      </div>

      <div className="list-cards">
        {menu.modifierLists.length === 0 && <p className="empty">No modification lists yet</p>}
        {menu.modifierLists.map((list) => {
          const used = usage(menu, list.id);
          return (
            <article key={list.id} className="list-card">
              <header>
                <h2>{list.name}</h2>
                <button className="secondary small" onClick={() => setEditing({ list })}>
                  Edit
                </button>
              </header>
              <div className="premods static">
                {list.actions.map((a) => (
                  <span key={a} className={`premod premod-${a}`}>
                    {actionLabel(a)}
                  </span>
                ))}
              </div>
              <div className="mod-chips">
                {list.modifiers.map((m) => (
                  <span key={m.id} className="mod-chip static">
                    {m.name}
                    {m.altName && <span className="item-alt"> {m.altName}</span>}
                    {m.priceCents > 0 && <small> {money(m.priceCents)}</small>}
                  </span>
                ))}
              </div>
              <p className="muted">
                {used.categories.length || used.dishes ? (
                  <>
                    Used by {used.categories.join(", ")}
                    {used.categories.length > 0 && used.dishes > 0 && " and "}
                    {used.dishes > 0 && `${used.dishes} single dish${used.dishes === 1 ? "" : "es"}`}
                  </>
                ) : (
                  "Not attached to any dish yet"
                )}
              </p>
            </article>
          );
        })}
      </div>

      {editing && (
        <ListEditor
          rid={rid}
          menu={menu}
          list={editing.list}
          onClose={() => setEditing(null)}
          onDone={() => {
            setEditing(null);
            void reload();
          }}
        />
      )}
    </main>
  );
}

function usage(menu: Menu, listId: string) {
  return {
    categories: menu.categories.filter((c) => c.modifierListIds.includes(listId)).map((c) => c.name),
    dishes: menu.categories.flatMap((c) => c.items).filter((i) => i.modifierListIds.includes(listId)).length,
  };
}

type Row = { id?: string; name: string; altName: string; priceCents: number | null };

function ListEditor({ rid, menu, list, onClose, onDone }: { rid: string; menu: Menu; list?: ModifierList; onClose(): void; onDone(): void }) {
  const [name, setName] = useState(list?.name ?? "");
  const [actions, setActions] = useState<PreModifier[]>(list?.actions ?? ["no", "extra"]);
  const [rows, setRows] = useState<Row[]>(list?.modifiers.map((m) => ({ ...m, altName: m.altName ?? "" })) ?? [{ name: "", altName: "", priceCents: 0 }]);
  const { busy, error, setError, run, runOk } = useAction();
  const charges = actions.some((a) => CHARGED_ACTIONS.includes(a));

  const setRow = (i: number, change: Partial<Row>) => setRows(rows.map((r, j) => (j === i ? { ...r, ...change } : r)));

  async function save() {
    const filled = rows.filter((r) => r.name.trim());
    if (filled.some((r) => r.priceCents === null)) return setError("Fix the prices marked in red");
    const input: ModifierListInput = {
      name,
      actions: PRE_MODIFIERS.map((p) => p.action).filter((a) => actions.includes(a)),
      modifiers: filled.map((r) => ({ id: r.id, name: r.name, altName: r.altName || null, priceCents: r.priceCents ?? 0 })),
    };
    if (await run(() => (list ? api.saveModifierList(rid, list.id, input) : api.addModifierList(rid, input)))) onDone();
  }

  async function remove() {
    if (!list) return;
    const used = usage(menu, list.id);
    const where = used.categories.length + used.dishes ? ` It is attached to ${used.categories.length} categories and ${used.dishes} dishes.` : "";
    if (!confirm(`Delete the ${list.name} list?${where}`)) return;
    if (await runOk(() => api.removeModifierList(rid, list.id))) onDone();
  }

  return (
    <Sheet title={list ? `Change ${list.name}` : "New modification list"} onClose={onClose} wide>
      <label className="stacked">
        List name
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Vegetables" />
      </label>

      <section className="sheet-section">
        <h3>Cashier can pick</h3>
        <div className="premods">
          {PRE_MODIFIERS.map((p) => {
            const on = actions.includes(p.action);
            return (
              <button
                key={p.action}
                className={`premod premod-${p.action}${on ? " active" : ""}`}
                aria-pressed={on}
                onClick={() => setActions(on ? actions.filter((a) => a !== p.action) : [...actions, p.action])}
              >
                {p.label}
              </button>
            );
          })}
        </div>
        <p className="muted small">ADD and MORE charge the price below. The others are free.</p>
      </section>

      <section className="sheet-section">
        <h3>Ingredients</h3>
        <div className="mod-rows">
          {rows.map((r, i) => (
            <div key={r.id ?? `new-${i}`} className="mod-row">
              <input value={r.name} onChange={(e) => setRow(i, { name: e.target.value })} placeholder="Name, e.g. Onion" aria-label="Ingredient name" />
              <input value={r.altName} onChange={(e) => setRow(i, { altName: e.target.value })} placeholder="Chinese, e.g. 洋蔥" aria-label="Chinese name" />
              {charges && <MoneyInput cents={r.priceCents} onChange={(c) => setRow(i, { priceCents: c })} label={`${r.name || "Ingredient"} price`} />}
              <button className="link" aria-label={`Remove ${r.name || "row"}`} onClick={() => setRows(rows.filter((_, j) => j !== i))}>
                ×
              </button>
            </div>
          ))}
          <button className="link" onClick={() => setRows([...rows, { name: "", altName: "", priceCents: 0 }])}>
            + Ingredient
          </button>
        </div>
      </section>

      {error && <Banner kind="error">{error}</Banner>}
      <footer className="sheet-foot">
        {list && (
          <button className="danger" onClick={remove} disabled={busy}>
            Delete list
          </button>
        )}
        <button className="primary" onClick={save} disabled={busy}>
          {busy ? "Saving…" : list ? "Save" : "Create list"}
        </button>
      </footer>
    </Sheet>
  );
}
