import type { LineModifier, Menu, MenuCategory, MenuItem, ModifierList, ModifierPick, PreModifier } from "./types";

// Front's words (the restaurant's old register), so the kitchen reads what it is used to.
export const PRE_MODIFIERS: { action: PreModifier; label: string }[] = [
  { action: "no", label: "NO" },
  { action: "add", label: "ADD" },
  { action: "extra", label: "MORE" },
  { action: "light", label: "LESS" },
  { action: "side", label: "ON SIDE" },
  { action: "sub", label: "CHANGE" },
  { action: "only", label: "ONLY" },
];

export const actionLabel = (action: PreModifier) => PRE_MODIFIERS.find((p) => p.action === action)!.label;

/** How a modifier reads on screen and on the ticket: "NO Onion", "Sauce ON SIDE". */
export function describeModifier(m: Pick<LineModifier, "action" | "name">): string {
  return m.action === "side" ? `${m.name} ON SIDE` : `${actionLabel(m.action)} ${m.name}`;
}

/** The modifier lists a dish offers: its category's lists, then its own, each once. */
export function listsForItem(item: MenuItem, category: MenuCategory | undefined, lists: ModifierList[]): ModifierList[] {
  const ids = [...new Set([...(category?.modifierListIds ?? []), ...item.modifierListIds])];
  return ids.flatMap((id) => lists.filter((l) => l.id === id));
}

export function findItem(menu: Menu, itemId: string): { item: MenuItem; category: MenuCategory } | undefined {
  for (const category of menu.categories) {
    const item = category.items.find((i) => i.id === itemId);
    if (item) return { item, category };
  }
  return undefined;
}

/**
 * Adds a pick, or takes it away when the same one is tapped again. A modifier appears once
 * per dish, so tapping EXTRA Onion after NO Onion replaces it.
 */
export function togglePick(picks: ModifierPick[], pick: ModifierPick): ModifierPick[] {
  const existing = picks.find((p) => p.modifierId === pick.modifierId);
  if (!existing) return [...picks, pick];
  if (existing.action === pick.action) return picks.filter((p) => p !== existing);
  return picks.map((p) => (p === existing ? pick : p));
}

/** Checks every pick against the dish's lists; returns the problem, or null when all is well. */
export function checkPicks(picks: ModifierPick[], lists: ModifierList[]): string | null {
  const seen = new Set<string>();
  for (const pick of picks) {
    if (seen.has(pick.modifierId)) return "Each modification can be picked once per dish";
    seen.add(pick.modifierId);
    const list = lists.find((l) => l.modifiers.some((m) => m.id === pick.modifierId));
    if (!list) return "That modification is not offered on this dish";
    if (!list.actions.includes(pick.action)) {
      const name = list.modifiers.find((m) => m.id === pick.modifierId)!.name;
      return `${actionLabel(pick.action)} is not allowed for ${name}`;
    }
  }
  return null;
}
