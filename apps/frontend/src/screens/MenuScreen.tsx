import { useEffect, useState } from "react";
import { Banner } from "../components/Banner";
import { ItemEditor } from "../components/ItemEditor";
import { MenuTabs } from "../components/MenuTabs";
import { api } from "../data/client";
import { useAction, useLoad } from "../data/hooks";
import { useRestaurant } from "../data/session";
import { money } from "../domain/money";
import { priceLabel } from "../domain/pricing";
import type { Menu, MenuCategory, MenuItem } from "../domain/types";

/**
 * Managers change dishes, categories and which modification lists apply. Cashiers see the
 * same list but can only mark dishes sold out and back on.
 */
export function MenuScreen() {
  const { rid, can } = useRestaurant();
  const { data: menu, error, reload, setData } = useLoad(() => api.menu(rid), [rid]);
  const [categoryId, setCategoryId] = useState<string>();
  const [editing, setEditing] = useState<{ item?: MenuItem } | null>(null);
  const [search, setSearch] = useState("");
  const action = useAction();
  const editor = can("menu");

  if (error) return <div className="splash">{error.message}</div>;
  if (!menu) return <div className="splash">Loading…</div>;

  const category = menu.categories.find((c) => c.id === categoryId) ?? menu.categories[0];
  const q = search.trim().toLowerCase();
  const items = q
    ? menu.categories.flatMap((c) => c.items).filter((i) => i.code.toLowerCase() === q || i.name.toLowerCase().includes(q))
    : (category?.items ?? []);
  const soldOut = menu.categories.flatMap((c) => c.items).filter((i) => !i.available);

  // Flip it on screen right away; put it back if the change didn't go through.
  async function setAvailable(item: MenuItem, available: boolean) {
    setData((m) => m && replaceItem(m, { ...item, available }));
    const saved = await action.run(() => api.setItemAvailable(rid, item.id, available));
    setData((m) => m && replaceItem(m, saved ?? item));
  }

  async function addCategory() {
    const name = prompt("Name of the new category");
    if (!name?.trim()) return;
    const created = await action.run(() => api.addCategory(rid, { name }));
    if (created) {
      await reload();
      setCategoryId(created.id);
    }
  }

  return (
    <main className="page">
      <MenuTabs />
      {!editor && <Banner kind="info">You can mark dishes sold out. Only a manager or owner can change dishes and prices.</Banner>}
      {soldOut.length > 0 && (
        <div className="sold-out-strip">
          <strong>Sold out now:</strong>
          {soldOut.map((i) => (
            <button key={i.id} className="chip" onClick={() => setAvailable(i, true)} disabled={action.busy}>
              {i.code && `${i.code} `}
              {i.name} · back on
            </button>
          ))}
        </div>
      )}
      {action.error && <Banner kind="error">{action.error}</Banner>}

      <div className="toolbar">
        <input type="search" placeholder="Find a dish by number or name" value={search} onChange={(e) => setSearch(e.target.value)} />
        {editor && category && (
          <button className="secondary" onClick={() => setEditing({})}>
            + Dish
          </button>
        )}
      </div>

      <div className="menu-admin">
        {!q && (
          <nav className="category-list">
            {menu.categories.map((c) => (
              <button key={c.id} className={c.id === category?.id ? "category-row active" : "category-row"} onClick={() => setCategoryId(c.id)}>
                <span>{c.name}</span>
                <span className="muted">{c.items.length}</span>
              </button>
            ))}
            {editor && (
              <button className="link" onClick={addCategory}>
                + Category
              </button>
            )}
          </nav>
        )}

        <section className="dish-list">
          {!q && category && editor && <CategoryEditor key={category.id} rid={rid} menu={menu} category={category} onSaved={reload} />}
          {items.length === 0 && <p className="empty">{q ? `No dish matches “${search}”` : "No dishes in this category yet"}</p>}
          {items.map((item) => (
            <div key={item.id} className={item.available ? "dish-row" : "dish-row sold-out"}>
              <span className="item-code">{item.code}</span>
              <span className="dish-name">
                <strong>{item.name}</strong> {item.spicy && <span className="spicy">★</span>}
                {item.altName && <span className="item-alt"> {item.altName}</span>}
                <span className="dish-meta">
                  {item.optionGroups
                    .filter((g) => g.name === "Choice")
                    .map((g) => g.options.map((o) => o.name).join(" or "))
                    .join("")}
                  {item.modifierListIds.length > 0 &&
                    ` + ${item.modifierListIds.map((id) => menu.modifierLists.find((l) => l.id === id)?.name).join(", ")}`}
                </span>
              </span>
              <span className="dish-price">{priceLabel(item, money)}</span>
              <label className="toggle">
                <input type="checkbox" checked={item.available} disabled={action.busy} onChange={(e) => setAvailable(item, e.target.checked)} />
                {item.available ? "On menu" : "Sold out"}
              </label>
              {editor && (
                <button className="secondary small" onClick={() => setEditing({ item })}>
                  Edit
                </button>
              )}
            </div>
          ))}
        </section>
      </div>

      {editing && category && (
        <ItemEditor
          rid={rid}
          menu={menu}
          item={editing.item}
          categoryId={editing.item?.categoryId ?? category.id}
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

function replaceItem(menu: Menu, item: MenuItem): Menu {
  return { ...menu, categories: menu.categories.map((c) => ({ ...c, items: c.items.map((i) => (i.id === item.id ? item : i)) })) };
}

/** Name, note and the modification lists every dish in the category offers. */
function CategoryEditor({ rid, menu, category, onSaved }: { rid: string; menu: Menu; category: MenuCategory; onSaved(): Promise<void> }) {
  const [name, setName] = useState(category.name);
  const [note, setNote] = useState(category.note ?? "");
  const [listIds, setListIds] = useState(category.modifierListIds);
  const [saved, setSaved] = useState(false);
  const { busy, error, run, runOk } = useAction();
  const dirty = name !== category.name || note !== (category.note ?? "") || listIds.join() !== category.modifierListIds.join();

  useEffect(() => setSaved(false), [name, note, listIds]);

  async function save() {
    if (await run(() => api.saveCategory(rid, category.id, { name, note, modifierListIds: listIds }))) {
      await onSaved();
      setSaved(true);
    }
  }

  async function remove() {
    if (!confirm(`Remove the ${category.name} category?`)) return;
    if (await runOk(() => api.removeCategory(rid, category.id))) await onSaved();
  }

  return (
    <div className="category-editor">
      <div className="row">
        <input className="category-name" value={name} onChange={(e) => setName(e.target.value)} aria-label="Category name" />
        <input className="grow" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Note shown to the cashier, e.g. With white rice." aria-label="Category note" />
      </div>
      <div className="row">
        <span className="muted">Every dish here offers:</span>
        {menu.modifierLists.map((l) => {
          const on = listIds.includes(l.id);
          return (
            <button key={l.id} className={on ? "chip small active" : "chip small"} onClick={() => setListIds(on ? listIds.filter((id) => id !== l.id) : [...listIds, l.id])}>
              {on ? "✓ " : ""}
              {l.name}
            </button>
          );
        })}
      </div>
      {error && <Banner kind="error">{error}</Banner>}
      <div className="row end">
        {category.items.length === 0 && (
          <button className="link danger-link" onClick={remove} disabled={busy}>
            Remove category
          </button>
        )}
        {saved && !dirty && <span className="saved">Saved ✓</span>}
        <button className="secondary small" onClick={save} disabled={busy || !dirty}>
          Save category
        </button>
      </div>
    </div>
  );
}
