// The cloud data model. These types are the contract the real API will implement;
// for now the mock API in src/data serves them. Money is integer cents, ids are strings.

export type Role = "owner" | "manager" | "cashier";

export interface User {
  id: string;
  name: string;
  email: string;
}

/** A person's place at one restaurant. Someone can work at several restaurants. */
export interface StaffMember {
  userId: string;
  restaurantId: string;
  name: string;
  email: string;
  role: Role;
  /** Whether a 4-digit PIN is set for switching staff on a shared tablet. */
  hasPin: boolean;
  active: boolean;
  /** Invited by email but has not signed in yet. */
  invited: boolean;
}

export interface RestaurantSummary {
  id: string;
  name: string;
  role: Role;
}

export type TicketLanguage = "en" | "zh" | "both";
export type PrinterKind = "none" | "epson_sdp" | "star_cloudprnt";
export type PrinterStatus = "online" | "offline";

/** A Wi-Fi receipt printer that fetches tickets from the cloud. */
export interface Printer {
  kind: PrinterKind;
  name: string;
  /** Serial number or device id the printer is registered with. */
  deviceId: string;
  status: PrinterStatus;
}

export interface Restaurant {
  id: string;
  name: string;
  phone: string;
  address: string;
  /** Sales tax as a decimal, e.g. 0.08875. */
  taxRate: number;
  /** IANA time zone; the business day and order numbers follow it. */
  timezone: string;
  ticketLanguage: TicketLanguage;
  printer: Printer;
}

export interface MenuOption {
  id: string;
  name: string;
  /** Added to the item's base price when chosen (sizes cost more, proteins usually don't). */
  extraCents: number;
}

/** A required pick of exactly one option, such as size (Pt/Qt) or protein (Chicken/Roast Pork). */
export interface MenuOptionGroup {
  name: string;
  options: MenuOption[];
}

/**
 * How a modifier changes a dish, as printed before the ingredient: NO Onion, EXTRA Sauce.
 * ADD, EXTRA and SUB charge the modifier's price; NO, LIGHT and ON SIDE are free.
 */
export type PreModifier = "no" | "add" | "extra" | "light" | "side" | "sub";

export interface Modifier {
  id: string;
  name: string;
  altName: string | null;
  /** Charged for ADD, EXTRA and SUB. */
  priceCents: number;
}

/** A shared list of modifiers (e.g. Vegetables) attached to categories or dishes. */
export interface ModifierList {
  id: string;
  name: string;
  /** Pre-modifiers the cashier may use with this list, in display order. */
  actions: PreModifier[];
  modifiers: Modifier[];
}

export interface MenuItem {
  id: string;
  categoryId: string;
  /** The number printed on the paper menu, e.g. "L6" or "19". Empty when there is none. */
  code: string;
  name: string;
  altName: string | null;
  /** Price before options; the cheapest size when the item has sizes. */
  priceCents: number;
  spicy: boolean;
  available: boolean;
  optionGroups: MenuOptionGroup[];
  /** Modifier lists for this dish only, on top of its category's lists. */
  modifierListIds: string[];
}

export interface MenuCategory {
  id: string;
  name: string;
  note: string | null;
  /** Modifier lists offered on every dish in the category. */
  modifierListIds: string[];
  items: MenuItem[];
}

export interface Menu {
  categories: MenuCategory[];
  modifierLists: ModifierList[];
}

/** What the menu editor sends to create or change a dish. */
export interface MenuItemInput {
  categoryId?: string;
  code?: string;
  name?: string;
  altName?: string | null;
  /** The price for a dish without sizes. */
  priceCents?: number;
  /** Each size with its full price, e.g. Pt $4.95 and Qt $7.60. Empty for one-price dishes. */
  sizes?: { name: string; priceCents: number }[];
  /** Protein or style picks, e.g. ["Chicken", "Roast Pork"]. */
  choices?: string[];
  spicy?: boolean;
  available?: boolean;
  modifierListIds?: string[];
}

export type OrderType = "walk_in" | "phone";
export type OrderStatus = "open" | "ready" | "picked_up" | "cancelled";

export interface ModifierPick {
  action: PreModifier;
  modifierId: string;
}

/** One line as the tablet sends it. `lineId` keeps a line's identity when an order is edited. */
export interface NewOrderLine {
  lineId?: string;
  menuItemId: string;
  quantity: number;
  /** One option id from each of the dish's option groups. */
  optionIds: string[];
  modifiers: ModifierPick[];
  note?: string;
}

export interface NewOrder {
  type: OrderType;
  customerName?: string;
  customerPhone?: string;
  /** When a phone order will be picked up (ISO time); empty means as soon as possible. */
  pickupAt?: string | null;
  note?: string;
  lines: NewOrderLine[];
}

export interface LineOption {
  optionId: string;
  group: string;
  name: string;
  extraCents: number;
}

export interface LineModifier {
  action: PreModifier;
  modifierId: string;
  name: string;
  altName: string | null;
  /** What this modifier adds to the line's unit price (0 for NO, LIGHT, ON SIDE). */
  priceCents: number;
}

/** A dish in an order, copied from the menu when the order was taken. */
export interface OrderLine {
  id: string;
  menuItemId: string;
  code: string;
  name: string;
  altName: string | null;
  quantity: number;
  basePriceCents: number;
  options: LineOption[];
  modifiers: LineModifier[];
  note: string | null;
}

export type TicketKind = "new" | "changed" | "cancelled" | "reprint" | "test";
export type PrintStatus = "queued" | "printed" | "failed" | "no_printer";

export type LineStyle = "normal" | "tall" | "big";
export interface TicketLine {
  text: string;
  style?: LineStyle;
  bold?: boolean;
  /** Printed reversed (white on black) to catch the chef's eye. */
  invert?: boolean;
}

export interface PrintJob {
  id: string;
  kind: TicketKind;
  status: PrintStatus;
  createdAt: string;
  /** The ticket exactly as it was sent to the printer. */
  ticket: TicketLine[];
}

export interface Order {
  id: string;
  restaurantId: string;
  /** Short number shown on the ticket; starts again at 1 each business day. */
  number: number;
  businessDate: string;
  type: OrderType;
  status: OrderStatus;
  customerName: string | null;
  customerPhone: string | null;
  pickupAt: string | null;
  note: string | null;
  lines: OrderLine[];
  subtotalCents: number;
  taxCents: number;
  totalCents: number;
  createdAt: string;
  updatedAt: string;
  readyAt: string | null;
  createdBy: { userId: string; name: string };
  /** Goes up by one every time the order is edited. */
  version: number;
  printJobs: PrintJob[];
}

export interface Settings {
  name: string;
  phone: string;
  address: string;
  taxRate: number;
  timezone: string;
  ticketLanguage: TicketLanguage;
  printer: Omit<Printer, "status">;
}
