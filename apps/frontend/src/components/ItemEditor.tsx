import { useState } from "react";
import { api } from "../data/client";
import { useAction } from "../data/hooks";
import type { Menu, MenuItem, MenuItemInput } from "../domain/types";
import { Banner } from "./Banner";
import { MoneyInput } from "./MoneyInput";
import { Sheet } from "./Sheet";

interface Props {
  rid: string;
  menu: Menu;
  /** The dish to change, or the category to add a new dish to. */
  item?: MenuItem;
  categoryId: string;
  onDone(): void;
  onClose(): void;
}

/** Adds or changes a dish: number, names, price or sizes, protein choices and modification lists. */
export function ItemEditor({ rid, menu, item, categoryId, onDone, onClose }: Props) {
  const sizeGroup = item?.optionGroups.find((g) => g.name === "Size");
  const [code, setCode] = useState(item?.code ?? "");
  const [name, setName] = useState(item?.name ?? "");
  const [altName, setAltName] = useState(item?.altName ?? "");
  const [category, setCategory] = useState(item?.categoryId ?? categoryId);
  const [priceCents, setPriceCents] = useState<number | null>(item && !sizeGroup ? item.priceCents : null);
  const [sizes, setSizes] = useState<{ name: string; priceCents: number | null }[]>(
    sizeGroup?.options.map((o) => ({ name: o.name, priceCents: item!.priceCents + o.extraCents })) ?? [],
  );
  const [choices, setChoices] = useState(item?.optionGroups.find((g) => g.name === "Choice")?.options.map((o) => o.name).join(", ") ?? "");
  const [spicy, setSpicy] = useState(item?.spicy ?? false);
  const [listIds, setListIds] = useState<string[]>(item?.modifierListIds ?? []);
  const { busy, error, setError, run, runOk } = useAction();

  const categoryLists = menu.categories.find((c) => c.id === category)?.modifierListIds ?? [];
  const hasSizes = sizes.length > 0;

  async function save() {
    if (hasSizes && sizes.some((s) => s.priceCents === null)) return setError("Every size needs a price");
    if (!hasSizes && priceCents === null) return setError("Enter a price");
    const input: MenuItemInput = {
      categoryId: category,
      code,
      name,
      altName: altName || null,
      priceCents: hasSizes ? undefined : priceCents!,
      sizes: sizes.map((s) => ({ name: s.name, priceCents: s.priceCents! })),
      choices: choices.split(",").map((c) => c.trim()).filter(Boolean),
      spicy,
      modifierListIds: listIds,
    };
    const saved = await run(() => (item ? api.saveItem(rid, item.id, input) : api.addItem(rid, { ...input, categoryId: category, name })));
    if (saved) onDone();
  }

  async function remove() {
    if (!item || !confirm(`Remove ${item.name} from the menu? Past orders keep it.`)) return;
    if (await runOk(() => api.removeItem(rid, item.id))) onDone();
  }

  return (
    <Sheet title={item ? `Change ${item.name}` : "Add a dish"} onClose={onClose} wide>
      <div className="form-grid">
        <label>
          Menu number
          <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="e.g. L6" autoCapitalize="characters" />
        </label>
        <label className="span-2">
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="General Tso's Chicken" />
        </label>
        <label>
          Chinese name
          <input value={altName} onChange={(e) => setAltName(e.target.value)} placeholder="左宗雞" />
        </label>
        <label className="span-2">
          Category
          <select value={category} onChange={(e) => setCategory(e.target.value)}>
            {menu.categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <section className="sheet-section">
        <h3>Price</h3>
        {!hasSizes ? (
          <div className="row">
            <MoneyInput cents={priceCents} onChange={setPriceCents} />
            <button
              className="link"
              onClick={() =>
                setSizes([
                  { name: "Pt", priceCents },
                  { name: "Qt", priceCents: null },
                ])
              }
            >
              + Sizes (Pt / Qt…)
            </button>
          </div>
        ) : (
          <div className="sizes">
            {sizes.map((s, i) => (
              <span key={i} className="size-input">
                <input className="size-name" value={s.name} aria-label="Size name" onChange={(e) => setSizes(sizes.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} />
                <MoneyInput cents={s.priceCents} label={`${s.name} price`} onChange={(c) => setSizes(sizes.map((x, j) => (j === i ? { ...x, priceCents: c } : x)))} />
                <button className="link" aria-label={`Remove ${s.name}`} onClick={() => setSizes(sizes.length > 2 ? sizes.filter((_, j) => j !== i) : [])}>
                  ×
                </button>
              </span>
            ))}
            <button className="link" onClick={() => setSizes([...sizes, { name: "", priceCents: null }])}>
              + Size
            </button>
          </div>
        )}
      </section>

      <section className="sheet-section">
        <h3>Protein or style choice</h3>
        <input value={choices} onChange={(e) => setChoices(e.target.value)} placeholder="e.g. Chicken, Roast Pork (leave empty if none)" />
      </section>

      <section className="sheet-section">
        <h3>Modification lists</h3>
        <div className="check-chips">
          {menu.modifierLists.map((l) => {
            const fromCategory = categoryLists.includes(l.id);
            const on = fromCategory || listIds.includes(l.id);
            return (
              <button
                key={l.id}
                className={on ? "chip active" : "chip"}
                disabled={fromCategory}
                title={fromCategory ? "Already on every dish in this category" : undefined}
                onClick={() => setListIds(on ? listIds.filter((id) => id !== l.id) : [...listIds, l.id])}
              >
                {on ? "✓ " : ""}
                {l.name}
                {fromCategory && <small> (category)</small>}
              </button>
            );
          })}
          {menu.modifierLists.length === 0 && <span className="muted">No lists yet. Make them under Menu › Modifier lists.</span>}
        </div>
      </section>

      <label className="check">
        <input type="checkbox" checked={spicy} onChange={(e) => setSpicy(e.target.checked)} /> Hot & spicy (★ on the menu)
      </label>

      {error && <Banner kind="error">{error}</Banner>}
      <footer className="sheet-foot">
        {item && (
          <button className="danger" onClick={remove} disabled={busy}>
            Remove dish
          </button>
        )}
        <button className="primary" onClick={save} disabled={busy}>
          {busy ? "Saving…" : item ? "Save" : "Add dish"}
        </button>
      </footer>
    </Sheet>
  );
}
