// Every call the screens make. The mock API implements it today; the cloud API will
// implement the same interface over HTTP (see docs/api-plan.md).
import type {
  Menu,
  MenuCategory,
  MenuItem,
  MenuItemInput,
  ModifierList,
  NewOrder,
  Order,
  OrderStatus,
  PrintJob,
  PrinterStatus,
  Restaurant,
  RestaurantSummary,
  Role,
  Settings,
  StaffMember,
  User,
} from "../domain/types";

export class ApiError extends Error {
  constructor(
    message: string,
    /** HTTP-style status: 400 bad input, 401 signed out, 403 not allowed, 404 not found, 409 conflict. */
    readonly status: number,
  ) {
    super(message);
  }
}

export interface Session {
  user: User;
  restaurants: RestaurantSummary[];
}

/** The restaurant plus who is working the tablet right now (changes with a PIN switch). */
export interface RestaurantContext {
  restaurant: Restaurant;
  me: StaffMember;
}

export interface ModifierListInput {
  name: string;
  actions: ModifierList["actions"];
  /** Existing modifiers keep their id so past orders and open carts stay linked. */
  modifiers: { id?: string; name: string; altName: string | null; priceCents: number }[];
}

export interface CategoryInput {
  name?: string;
  note?: string | null;
  modifierListIds?: string[];
}

export interface StaffInput {
  name?: string;
  role?: Role;
  active?: boolean;
  /** Four digits, or null to remove the PIN. */
  pin?: string | null;
}

export interface Api {
  // Sign-in
  login(email: string, password: string): Promise<Session>;
  /** The signed-in session on this device, or null. */
  session(): Promise<Session | null>;
  logout(): Promise<void>;

  // Restaurant
  restaurant(rid: string): Promise<RestaurantContext>;
  /** Shared tablet: whoever enters their PIN becomes the person taking orders. */
  switchStaff(rid: string, pin: string): Promise<StaffMember>;
  saveSettings(rid: string, changes: Partial<Settings>): Promise<Restaurant>;
  printTestTicket(rid: string): Promise<PrintJob>;
  /** Demo only: pretend the printer went offline or came back. */
  setPrinterStatus(rid: string, status: PrinterStatus): Promise<Restaurant>;

  // Menu
  menu(rid: string): Promise<Menu>;
  addCategory(rid: string, input: { name: string; note?: string | null }): Promise<MenuCategory>;
  saveCategory(rid: string, categoryId: string, changes: CategoryInput): Promise<MenuCategory>;
  /** Only an empty category can be removed. */
  removeCategory(rid: string, categoryId: string): Promise<void>;
  addItem(rid: string, input: MenuItemInput & { categoryId: string; name: string }): Promise<MenuItem>;
  saveItem(rid: string, itemId: string, changes: MenuItemInput): Promise<MenuItem>;
  removeItem(rid: string, itemId: string): Promise<void>;
  /** Sold out / back on: allowed for every role, unlike other menu changes. */
  setItemAvailable(rid: string, itemId: string, available: boolean): Promise<MenuItem>;
  addModifierList(rid: string, input: ModifierListInput): Promise<ModifierList>;
  saveModifierList(rid: string, listId: string, input: ModifierListInput): Promise<ModifierList>;
  /** Also detaches the list from every category and dish. */
  removeModifierList(rid: string, listId: string): Promise<void>;

  // Orders
  /** Today's orders (restaurant time), newest first. */
  orders(rid: string): Promise<Order[]>;
  order(rid: string, orderId: string): Promise<Order>;
  /** Saves the order and queues a kitchen ticket. The order is kept even if printing fails. */
  createOrder(rid: string, input: NewOrder): Promise<Order>;
  /** Replaces the order's lines and details and prints a CHANGED ticket. */
  updateOrder(rid: string, orderId: string, input: NewOrder): Promise<Order>;
  cancelOrder(rid: string, orderId: string): Promise<Order>;
  reprint(rid: string, orderId: string): Promise<Order>;
  setOrderStatus(rid: string, orderId: string, status: Exclude<OrderStatus, "cancelled">): Promise<Order>;

  // Staff
  staff(rid: string): Promise<StaffMember[]>;
  inviteStaff(rid: string, input: { name: string; email: string; role: Role }): Promise<StaffMember>;
  saveStaff(rid: string, userId: string, changes: StaffInput): Promise<StaffMember>;
}
