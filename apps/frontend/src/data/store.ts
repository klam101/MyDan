// The mock API's tables. They mirror what the cloud Postgres database will hold: every
// restaurant-owned row carries its restaurantId.
import { buildLine, OrderError } from "../domain/lines";
import { totals } from "../domain/pricing";
import { buildTicket } from "../domain/ticket";
import type {
  Menu,
  MenuCategory,
  MenuItem,
  ModifierList,
  NewOrder,
  Order,
  PrintJob,
  Restaurant,
  Role,
  TicketKind,
  User,
} from "../domain/types";

export interface MembershipRow {
  userId: string;
  restaurantId: string;
  role: Role;
  pin: string | null;
  active: boolean;
  invited: boolean;
}

export type CategoryRow = Omit<MenuCategory, "items"> & { restaurantId: string; sortOrder: number };
export type ItemRow = MenuItem & { restaurantId: string; sortOrder: number };
export type ModifierListRow = ModifierList & { restaurantId: string };

export interface MockDb {
  users: (User & { password: string })[];
  memberships: MembershipRow[];
  restaurants: Restaurant[];
  categories: CategoryRow[];
  items: ItemRow[];
  modifierLists: ModifierListRow[];
  orders: Order[];
}

let counter = 0;
/**
 * Short unique ids. crypto.randomUUID is not available to pages served over plain http
 * on the local network (the iPad during development), so this avoids it.
 */
export const newId = (prefix: string) =>
  `${prefix}_${Date.now().toString(36)}${(counter++).toString(36)}${Math.random().toString(36).slice(2, 6)}`;

/** The restaurant's calendar date, YYYY-MM-DD. Order numbers restart each day. */
export const businessDate = (now: Date, timezone: string) => now.toLocaleDateString("en-CA", { timeZone: timezone });

export function assembleMenu(db: MockDb, rid: string): Menu {
  const strip = <T extends { restaurantId: string }>({ restaurantId: _, ...rest }: T) => rest;
  const items = db.items
    .filter((i) => i.restaurantId === rid)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map(({ restaurantId: _, sortOrder: __, ...item }): MenuItem => item);
  return {
    categories: db.categories
      .filter((c) => c.restaurantId === rid)
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map(({ restaurantId: _, sortOrder: __, ...c }) => ({ ...c, items: items.filter((i) => i.categoryId === c.id) })),
    modifierLists: db.modifierLists.filter((l) => l.restaurantId === rid).map(strip),
  };
}

/** Works out the stored base price and size options: the base is the cheapest size. */
export function priceModel(
  priceCents: number | undefined,
  sizes: { name: string; priceCents: number }[] = [],
): { base: number; sizes: { name: string; extraCents: number }[] } | string {
  if (sizes.length === 1) return "A dish with sizes needs at least two of them";
  if (sizes.some((s) => !s.name.trim())) return "Every size needs a name";
  if (new Set(sizes.map((s) => s.name.trim())).size !== sizes.length) return "Size names must be different";
  if (sizes.length === 0) {
    if (priceCents === undefined || priceCents < 0) return "A price is required";
    return { base: priceCents, sizes: [] };
  }
  const base = Math.min(...sizes.map((s) => s.priceCents));
  return { base, sizes: sizes.map((s) => ({ name: s.name.trim(), extraCents: s.priceCents - base })) };
}

/** Checks the order's details and builds its lines from the current menu. Throws OrderError. */
export function composeOrder(db: MockDb, rid: string, input: NewOrder): Pick<
  Order,
  "type" | "customerName" | "customerPhone" | "pickupAt" | "note" | "lines" | "subtotalCents" | "taxCents" | "totalCents"
> {
  const restaurant = db.restaurants.find((r) => r.id === rid)!;
  if (!input.lines.length) throw new OrderError("An order needs at least one dish");
  if (input.type !== "walk_in" && input.type !== "phone") throw new OrderError("Pick walk-in or phone");
  const customerName = input.customerName?.trim() || null;
  const customerPhone = input.customerPhone?.trim() || null;
  if (input.type === "phone" && !customerName && !customerPhone) {
    throw new OrderError("A phone order needs the customer's name or phone number");
  }
  const menu = assembleMenu(db, rid);
  const lines = input.lines.map((l) => buildLine(menu, l, () => newId("line")));
  return {
    type: input.type,
    customerName,
    customerPhone,
    pickupAt: input.pickupAt || null,
    note: input.note?.trim() || null,
    lines,
    ...totals(lines, restaurant.taxRate),
  };
}

/** A kitchen ticket waiting for the printer to fetch it (or shown on screen only, with no printer). */
export function printJob(restaurant: Restaurant, order: Order, kind: TicketKind, now: Date, previous?: Order): PrintJob {
  return {
    id: newId("job"),
    kind,
    status: restaurant.printer.kind === "none" ? "no_printer" : "queued",
    createdAt: now.toISOString(),
    ticket: buildTicket(order, kind, { language: restaurant.ticketLanguage, timezone: restaurant.timezone, previous }),
  };
}
