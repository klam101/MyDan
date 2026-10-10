import { checkPicks, findItem, listsForItem } from "./modifiers";
import { modifierCharge } from "./pricing";
import type { LineModifier, LineOption, Menu, NewOrderLine, OrderLine } from "./types";

export class OrderError extends Error {}

/**
 * Turns what the tablet sent into an order line, copying names and prices from the menu so
 * later menu changes don't rewrite past orders. Throws OrderError when the line is not valid.
 */
export function buildLine(menu: Menu, input: NewOrderLine, newId: () => string): OrderLine {
  const found = findItem(menu, input.menuItemId);
  if (!found) throw new OrderError("That dish is no longer on the menu");
  const { item, category } = found;
  if (!item.available) throw new OrderError(`${item.name} is sold out`);
  if (!Number.isInteger(input.quantity) || input.quantity < 1 || input.quantity > 99) {
    throw new OrderError("Quantity must be a whole number from 1 to 99");
  }

  const options: LineOption[] = item.optionGroups.map((group) => {
    const picked = group.options.filter((o) => input.optionIds.includes(o.id));
    if (picked.length !== 1) throw new OrderError(`Pick one ${group.name === "Choice" ? "choice" : group.name.toLowerCase()} for ${item.name}`);
    return { optionId: picked[0].id, group: group.name, name: picked[0].name, extraCents: picked[0].extraCents };
  });
  if (input.optionIds.length !== options.length) throw new OrderError(`${item.name} has an option that doesn't belong to it`);

  const lists = listsForItem(item, category, menu.modifierLists);
  const problem = checkPicks(input.modifiers, lists);
  if (problem) throw new OrderError(`${item.name}: ${problem}`);
  const modifiers: LineModifier[] = input.modifiers.map((pick) => {
    const modifier = lists.flatMap((l) => l.modifiers).find((m) => m.id === pick.modifierId)!;
    return {
      action: pick.action,
      modifierId: modifier.id,
      name: modifier.name,
      altName: modifier.altName,
      priceCents: modifierCharge(pick.action, modifier.priceCents),
    };
  });

  return {
    id: input.lineId ?? newId(),
    menuItemId: item.id,
    code: item.code,
    name: item.name,
    altName: item.altName,
    quantity: input.quantity,
    basePriceCents: item.priceCents,
    options,
    modifiers,
    note: input.note?.trim() || null,
  };
}

/** The input that rebuilds a saved line, for editing an order. */
export const lineInput = (line: OrderLine): NewOrderLine => ({
  lineId: line.id,
  menuItemId: line.menuItemId,
  quantity: line.quantity,
  optionIds: line.options.map((o) => o.optionId),
  modifiers: line.modifiers.map((m) => ({ action: m.action, modifierId: m.modifierId })),
  note: line.note ?? undefined,
});

/** True when two versions of a line would cook differently. */
export function lineChanged(a: OrderLine, b: OrderLine): boolean {
  const key = (l: OrderLine) =>
    JSON.stringify([l.menuItemId, l.quantity, l.options.map((o) => o.optionId), l.modifiers.map((m) => [m.action, m.modifierId]), l.note]);
  return key(a) !== key(b);
}
