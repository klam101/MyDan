import type { MenuItem, OrderLine, PreModifier } from "./types";

/** Pre-modifiers that charge the modifier's price: only ADD and MORE, as on Front. */
export const CHARGED_ACTIONS: readonly PreModifier[] = ["add", "extra"];

export const modifierCharge = (action: PreModifier, priceCents: number) =>
  CHARGED_ACTIONS.includes(action) ? priceCents : 0;

export type PricedLine = Pick<OrderLine, "basePriceCents" | "options" | "modifiers" | "quantity">;

export const unitPrice = (line: Omit<PricedLine, "quantity">) =>
  line.basePriceCents +
  line.options.reduce((sum, o) => sum + o.extraCents, 0) +
  line.modifiers.reduce((sum, m) => sum + m.priceCents, 0);

export const linePrice = (line: PricedLine) => unitPrice(line) * line.quantity;

export function totals(lines: PricedLine[], taxRate: number) {
  const subtotalCents = lines.reduce((sum, l) => sum + linePrice(l), 0);
  const taxCents = Math.round(subtotalCents * taxRate);
  return { subtotalCents, taxCents, totalCents: subtotalCents + taxCents };
}

/** "$6.35", or the lowest and highest price for dishes whose price depends on size. */
export function priceLabel(item: MenuItem, format: (cents: number) => string): string {
  const size = item.optionGroups.find((g) => g.name === "Size");
  if (!size) return format(item.priceCents);
  const extras = size.options.map((o) => o.extraCents);
  return `${format(item.priceCents + Math.min(...extras))}–${format(item.priceCents + Math.max(...extras))}`;
}
