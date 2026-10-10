// The mock API: the whole backend in the browser, saved to localStorage so a refresh keeps
// the demo going. It checks sign-in, restaurant membership and roles the way the cloud API will.
import { OrderError } from "../domain/lines";
import { can, canManage, assignableRoles, type Permission } from "../domain/permissions";
import { buildTestTicket } from "../domain/ticket";
import type {
  MenuCategory,
  MenuItem,
  MenuItemInput,
  MenuOptionGroup,
  ModifierList,
  NewOrder,
  Order,
  PrintJob,
  Restaurant,
  StaffMember,
} from "../domain/types";
import { ApiError, type Api, type ModifierListInput, type Session } from "./api";
import { DEMO_PASSWORD, seedDb } from "./seed";
import { assembleMenu, businessDate, composeOrder, newId, priceModel, printJob, type MembershipRow, type MockDb } from "./store";

const DB_KEY = "orders-demo-db-v1";
const SESSION_KEY = "orders-demo-session-v1";

interface SessionState {
  userId: string;
  /** Per restaurant: who is working the tablet after a PIN switch. */
  acting: Record<string, string>;
}

export interface MockOptions {
  /** Pretend network delay in ms. */
  latency?: number;
  /** How long a cloud printer takes to fetch and print a ticket, in ms. */
  printDelay?: number;
  /** Keep data in localStorage (off in tests). */
  persist?: boolean;
  now?: () => Date;
}

function load<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function save(key: string, value: unknown): void {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Private browsing or full storage: the demo still works until the page reloads.
  }
}

const clone = <T>(value: T): T => structuredClone(value);
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export interface MockApi extends Api {
  /** Throws away all changes and starts the demo over. */
  resetDemo(): void;
}

export function createMockApi({ latency = 150, printDelay = 2000, persist = true, now = () => new Date() }: MockOptions = {}): MockApi {
  let db: MockDb = (persist && load<MockDb>(DB_KEY)) || seedDb(now());
  let session: SessionState | null = persist ? load<SessionState>(SESSION_KEY) : null;

  const commit = () => {
    if (persist) {
      save(DB_KEY, db);
      save(SESSION_KEY, session);
    }
  };
  commit();

  /** Runs a call after the pretend network delay, saving any changes. */
  async function call<T>(fn: () => T): Promise<T> {
    if (latency) await sleep(latency);
    const result = fn();
    commit();
    return clone(result);
  }

  // --- Access checks -------------------------------------------------------------------------

  function signedIn(): SessionState {
    if (!session || !db.users.some((u) => u.id === session!.userId)) throw new ApiError("Please sign in again", 401);
    return session;
  }

  /** The restaurant and the person acting at it, checked against a permission. */
  function scope(rid: string, permission?: Permission): { restaurant: Restaurant; member: MembershipRow } {
    const s = signedIn();
    const restaurant = db.restaurants.find((r) => r.id === rid);
    const own = db.memberships.find((m) => m.userId === s.userId && m.restaurantId === rid && m.active);
    if (!restaurant || !own) throw new ApiError("Restaurant not found", 404);
    const actingId = s.acting[rid] ?? s.userId;
    const member = db.memberships.find((m) => m.userId === actingId && m.restaurantId === rid && m.active) ?? own;
    if (permission && !can(member.role, permission)) throw new ApiError("Your role can't do that", 403);
    return { restaurant, member };
  }

  const staffMember = (m: MembershipRow): StaffMember => {
    const user = db.users.find((u) => u.id === m.userId)!;
    return {
      userId: m.userId,
      restaurantId: m.restaurantId,
      name: user.name,
      email: user.email,
      role: m.role,
      hasPin: m.pin !== null,
      active: m.active,
      invited: m.invited,
    };
  };

  const sessionView = (s: SessionState): Session => {
    const user = db.users.find((u) => u.id === s.userId)!;
    return {
      user: { id: user.id, name: user.name, email: user.email },
      restaurants: db.memberships
        .filter((m) => m.userId === s.userId && m.active)
        .map((m) => ({ id: m.restaurantId, name: db.restaurants.find((r) => r.id === m.restaurantId)!.name, role: m.role })),
    };
  };

  // --- Menu helpers --------------------------------------------------------------------------

  const category = (rid: string, id: string) => {
    const row = db.categories.find((c) => c.restaurantId === rid && c.id === id);
    if (!row) throw new ApiError("Category not found", 404);
    return row;
  };
  const item = (rid: string, id: string) => {
    const row = db.items.find((i) => i.restaurantId === rid && i.id === id);
    if (!row) throw new ApiError("Dish not found", 404);
    return row;
  };
  const modifierList = (rid: string, id: string) => {
    const row = db.modifierLists.find((l) => l.restaurantId === rid && l.id === id);
    if (!row) throw new ApiError("Modifier list not found", 404);
    return row;
  };
  const categoryView = (rid: string, id: string): MenuCategory => assembleMenu(db, rid).categories.find((c) => c.id === id)!;
  const itemView = ({ restaurantId: _, sortOrder: __, ...rest }: (typeof db.items)[number]): MenuItem => rest;

  function checkListIds(rid: string, ids: string[]): string[] {
    for (const id of ids) modifierList(rid, id);
    return [...new Set(ids)];
  }

  /** Rebuilds a dish's option groups, keeping the ids of sizes and choices that stay. */
  function optionGroups(current: MenuOptionGroup[], sizes: { name: string; extraCents: number }[] | undefined, choices: string[] | undefined): MenuOptionGroup[] {
    const keep = (group: string, name: string) =>
      current.find((g) => g.name === group)?.options.find((o) => o.name === name)?.id ?? newId("opt");
    const groups: MenuOptionGroup[] = [];
    const sizeOptions = sizes ?? current.find((g) => g.name === "Size")?.options ?? [];
    if (sizeOptions.length) groups.push({ name: "Size", options: sizeOptions.map((s) => ({ id: keep("Size", s.name), name: s.name, extraCents: s.extraCents })) });
    const choiceNames = choices ?? current.find((g) => g.name === "Choice")?.options.map((o) => o.name) ?? [];
    const cleaned = [...new Set(choiceNames.map((c) => c.trim()).filter(Boolean))];
    if (cleaned.length === 1) throw new ApiError("A dish with choices needs at least two of them", 400);
    if (cleaned.length) groups.push({ name: "Choice", options: cleaned.map((c) => ({ id: keep("Choice", c), name: c, extraCents: 0 })) });
    return groups;
  }

  function modifierListFrom(rid: string, input: ModifierListInput, current?: ModifierList): Omit<ModifierList, "id"> {
    const name = input.name.trim();
    if (!name) throw new ApiError("The list needs a name", 400);
    if (db.modifierLists.some((l) => l.restaurantId === rid && l.id !== current?.id && l.name.toLowerCase() === name.toLowerCase())) {
      throw new ApiError(`There is already a list called ${name}`, 409);
    }
    if (!input.actions.length) throw new ApiError("Pick at least one of NO, ADD, EXTRA, LIGHT, ON SIDE, SUB", 400);
    const modifiers = input.modifiers.map((m) => {
      if (!m.name.trim()) throw new ApiError("Every modification needs a name", 400);
      if (!Number.isInteger(m.priceCents) || m.priceCents < 0) throw new ApiError(`${m.name}: price must be zero or more`, 400);
      const id = m.id && current?.modifiers.some((c) => c.id === m.id) ? m.id : newId("mod");
      return { id, name: m.name.trim(), altName: m.altName?.trim() || null, priceCents: m.priceCents };
    });
    if (!modifiers.length) throw new ApiError("Add at least one modification", 400);
    return { name, actions: [...new Set(input.actions)], modifiers };
  }

  // --- Order helpers -------------------------------------------------------------------------

  const order = (rid: string, id: string) => {
    const row = db.orders.find((o) => o.restaurantId === rid && o.id === id);
    if (!row) throw new ApiError("Order not found", 404);
    return row;
  };

  /** Pretends the cloud printer fetches the ticket a moment later. */
  function deliver(rid: string, job: PrintJob, orderId?: string): void {
    if (job.status !== "queued") return;
    setTimeout(() => {
      const restaurant = db.restaurants.find((r) => r.id === rid);
      const target = orderId ? db.orders.find((o) => o.id === orderId)?.printJobs.find((j) => j.id === job.id) : job;
      if (!restaurant || !target || target.status !== "queued") return;
      target.status = restaurant.printer.status === "online" ? "printed" : "failed";
      commit();
    }, printDelay);
  }

  function addJob(restaurant: Restaurant, o: Order, kind: PrintJob["kind"], previous?: Order): void {
    const job = printJob(restaurant, o, kind, now(), previous);
    o.printJobs.push(job);
    deliver(restaurant.id, job, o.id);
  }

  function orderInput<T>(fn: () => T): T {
    try {
      return fn();
    } catch (e) {
      if (e instanceof OrderError) throw new ApiError(e.message, 400);
      throw e;
    }
  }

  // --- The API -------------------------------------------------------------------------------

  const api: MockApi = {
    resetDemo() {
      db = seedDb(now());
      session = null;
      commit();
    },

    login: (email, password) =>
      call(() => {
        const user = db.users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase());
        if (!user || password !== user.password) throw new ApiError("Email or password is wrong", 401);
        if (!db.memberships.some((m) => m.userId === user.id && m.active)) throw new ApiError("This account has no restaurant yet", 403);
        db.memberships.filter((m) => m.userId === user.id).forEach((m) => (m.invited = false));
        session = { userId: user.id, acting: {} };
        return sessionView(session);
      }),

    session: () => call(() => (session && db.users.some((u) => u.id === session!.userId) ? sessionView(session) : null)),

    logout: () =>
      call(() => {
        session = null;
      }),

    restaurant: (rid) =>
      call(() => {
        const { restaurant, member } = scope(rid);
        return { restaurant, me: staffMember(member) };
      }),

    switchStaff: (rid, pin) =>
      call(() => {
        scope(rid);
        const member = db.memberships.find((m) => m.restaurantId === rid && m.active && m.pin !== null && m.pin === pin);
        if (!member) throw new ApiError("That PIN doesn't match anyone here", 401);
        session!.acting[rid] = member.userId;
        return staffMember(member);
      }),

    saveSettings: (rid, changes) =>
      call(() => {
        const { restaurant } = scope(rid, "settings");
        if (changes.name !== undefined && !changes.name.trim()) throw new ApiError("The restaurant needs a name", 400);
        if (changes.taxRate !== undefined && !(changes.taxRate >= 0 && changes.taxRate < 0.5)) throw new ApiError("Tax rate looks wrong", 400);
        const { printer, ...rest } = changes;
        Object.assign(restaurant, rest);
        if (printer) Object.assign(restaurant.printer, printer);
        return restaurant;
      }),

    printTestTicket: (rid) =>
      call(() => {
        const { restaurant } = scope(rid, "settings");
        if (restaurant.printer.kind === "none") throw new ApiError("Set up a printer first", 400);
        const job: PrintJob = { id: newId("job"), kind: "test", status: "queued", createdAt: now().toISOString(), ticket: buildTestTicket(restaurant, now()) };
        deliver(rid, job);
        return job;
      }),

    setPrinterStatus: (rid, status) =>
      call(() => {
        const { restaurant } = scope(rid, "settings");
        restaurant.printer.status = status;
        return restaurant;
      }),

    menu: (rid) =>
      call(() => {
        scope(rid);
        return assembleMenu(db, rid);
      }),

    addCategory: (rid, input) =>
      call(() => {
        scope(rid, "menu");
        if (!input.name.trim()) throw new ApiError("The category needs a name", 400);
        const id = newId("cat");
        const sortOrder = Math.max(-1, ...db.categories.filter((c) => c.restaurantId === rid).map((c) => c.sortOrder)) + 1;
        db.categories.push({ id, restaurantId: rid, name: input.name.trim(), note: input.note?.trim() || null, modifierListIds: [], sortOrder });
        return categoryView(rid, id);
      }),

    saveCategory: (rid, categoryId, changes) =>
      call(() => {
        scope(rid, "menu");
        const row = category(rid, categoryId);
        if (changes.name !== undefined) {
          if (!changes.name.trim()) throw new ApiError("The category needs a name", 400);
          row.name = changes.name.trim();
        }
        if (changes.note !== undefined) row.note = changes.note?.trim() || null;
        if (changes.modifierListIds) row.modifierListIds = checkListIds(rid, changes.modifierListIds);
        return categoryView(rid, categoryId);
      }),

    removeCategory: (rid, categoryId) =>
      call(() => {
        scope(rid, "menu");
        category(rid, categoryId);
        if (db.items.some((i) => i.categoryId === categoryId)) throw new ApiError("Move or remove its dishes first", 409);
        db.categories = db.categories.filter((c) => c.id !== categoryId);
      }),

    addItem: (rid, input) =>
      call(() => {
        scope(rid, "menu");
        category(rid, input.categoryId);
        if (!input.name.trim()) throw new ApiError("The dish needs a name", 400);
        const model = priceModel(input.priceCents, input.sizes);
        if (typeof model === "string") throw new ApiError(model, 400);
        const sortOrder = Math.max(-1, ...db.items.filter((i) => i.categoryId === input.categoryId).map((i) => i.sortOrder)) + 1;
        const row = {
          id: newId("item"),
          restaurantId: rid,
          categoryId: input.categoryId,
          code: input.code?.trim() ?? "",
          name: input.name.trim(),
          altName: input.altName?.trim() || null,
          priceCents: model.base,
          spicy: input.spicy ?? false,
          available: input.available ?? true,
          optionGroups: optionGroups([], model.sizes, input.choices),
          modifierListIds: checkListIds(rid, input.modifierListIds ?? []),
          sortOrder,
        };
        db.items.push(row);
        return itemView(row);
      }),

    saveItem: (rid, itemId, changes: MenuItemInput) =>
      call(() => {
        scope(rid, "menu");
        const row = item(rid, itemId);
        if (changes.categoryId) category(rid, changes.categoryId);
        if (changes.name !== undefined && !changes.name.trim()) throw new ApiError("The dish needs a name", 400);
        let sizes: { name: string; extraCents: number }[] | undefined;
        if (changes.sizes !== undefined || changes.priceCents !== undefined) {
          const model = priceModel(changes.priceCents ?? (changes.sizes?.length ? undefined : row.priceCents), changes.sizes ?? []);
          if (typeof model === "string") throw new ApiError(model, 400);
          row.priceCents = model.base;
          sizes = model.sizes;
        }
        row.optionGroups = optionGroups(row.optionGroups, sizes, changes.choices);
        if (changes.categoryId) row.categoryId = changes.categoryId;
        if (changes.code !== undefined) row.code = changes.code.trim();
        if (changes.name !== undefined) row.name = changes.name.trim();
        if (changes.altName !== undefined) row.altName = changes.altName?.trim() || null;
        if (changes.spicy !== undefined) row.spicy = changes.spicy;
        if (changes.available !== undefined) row.available = changes.available;
        if (changes.modifierListIds) row.modifierListIds = checkListIds(rid, changes.modifierListIds);
        return itemView(row);
      }),

    removeItem: (rid, itemId) =>
      call(() => {
        scope(rid, "menu");
        item(rid, itemId);
        db.items = db.items.filter((i) => i.id !== itemId);
      }),

    setItemAvailable: (rid, itemId, available) =>
      call(() => {
        scope(rid, "soldOut");
        const row = item(rid, itemId);
        row.available = available;
        return itemView(row);
      }),

    addModifierList: (rid, input) =>
      call(() => {
        scope(rid, "menu");
        const list = { id: newId("mlist"), ...modifierListFrom(rid, input) };
        db.modifierLists.push({ ...list, restaurantId: rid });
        return list;
      }),

    saveModifierList: (rid, listId, input) =>
      call(() => {
        scope(rid, "menu");
        const row = modifierList(rid, listId);
        Object.assign(row, modifierListFrom(rid, input, row));
        const { restaurantId: _, ...list } = row;
        return list;
      }),

    removeModifierList: (rid, listId) =>
      call(() => {
        scope(rid, "menu");
        modifierList(rid, listId);
        db.modifierLists = db.modifierLists.filter((l) => l.id !== listId);
        for (const row of [...db.categories, ...db.items]) {
          if (row.restaurantId === rid) row.modifierListIds = row.modifierListIds.filter((id) => id !== listId);
        }
      }),

    orders: (rid) =>
      call(() => {
        const { restaurant } = scope(rid, "orders");
        const today = businessDate(now(), restaurant.timezone);
        return db.orders.filter((o) => o.restaurantId === rid && o.businessDate === today).sort((a, b) => b.number - a.number);
      }),

    order: (rid, orderId) =>
      call(() => {
        scope(rid, "orders");
        return order(rid, orderId);
      }),

    createOrder: (rid, input: NewOrder) =>
      call(() => {
        const { restaurant, member } = scope(rid, "order");
        const details = orderInput(() => composeOrder(db, rid, input));
        const at = now();
        const date = businessDate(at, restaurant.timezone);
        const number = Math.max(0, ...db.orders.filter((o) => o.restaurantId === rid && o.businessDate === date).map((o) => o.number)) + 1;
        const user = db.users.find((u) => u.id === member.userId)!;
        const created: Order = {
          id: newId("ord"),
          restaurantId: rid,
          number,
          businessDate: date,
          status: "open",
          ...details,
          createdAt: at.toISOString(),
          updatedAt: at.toISOString(),
          readyAt: null,
          createdBy: { userId: user.id, name: user.name },
          version: 1,
          printJobs: [],
        };
        db.orders.push(created);
        addJob(restaurant, created, "new");
        return created;
      }),

    updateOrder: (rid, orderId, input) =>
      call(() => {
        const { restaurant } = scope(rid, "order");
        const row = order(rid, orderId);
        if (row.status === "cancelled" || row.status === "picked_up") throw new ApiError("This order is closed and can't be changed", 409);
        const previous = clone(row);
        Object.assign(row, orderInput(() => composeOrder(db, rid, input)));
        row.version += 1;
        row.updatedAt = now().toISOString();
        addJob(restaurant, row, "changed", previous);
        return row;
      }),

    cancelOrder: (rid, orderId) =>
      call(() => {
        const { restaurant } = scope(rid, "order");
        const row = order(rid, orderId);
        if (row.status === "cancelled") return row;
        if (row.status === "picked_up") throw new ApiError("This order was already picked up", 409);
        row.status = "cancelled";
        row.updatedAt = now().toISOString();
        addJob(restaurant, row, "cancelled");
        return row;
      }),

    reprint: (rid, orderId) =>
      call(() => {
        const { restaurant } = scope(rid, "orders");
        const row = order(rid, orderId);
        addJob(restaurant, row, row.status === "cancelled" ? "cancelled" : "reprint");
        return row;
      }),

    setOrderStatus: (rid, orderId, status) =>
      call(() => {
        scope(rid, "kitchen");
        const row = order(rid, orderId);
        if (row.status === "cancelled") throw new ApiError("This order was cancelled", 409);
        row.status = status;
        row.readyAt = status === "open" ? null : (row.readyAt ?? now().toISOString());
        row.updatedAt = now().toISOString();
        return row;
      }),

    staff: (rid) =>
      call(() => {
        scope(rid, "staff");
        const rank = { owner: 0, manager: 1, cashier: 2 };
        return db.memberships
          .filter((m) => m.restaurantId === rid)
          .map(staffMember)
          .sort((a, b) => Number(b.active) - Number(a.active) || rank[a.role] - rank[b.role] || a.name.localeCompare(b.name));
      }),

    inviteStaff: (rid, input) =>
      call(() => {
        const { member: actor } = scope(rid, "staff");
        if (!assignableRoles(actor.role).includes(input.role)) throw new ApiError("Your role can't add that kind of staff", 403);
        const email = input.email.trim().toLowerCase();
        if (!/^\S+@\S+\.\S+$/.test(email)) throw new ApiError("That email doesn't look right", 400);
        if (!input.name.trim()) throw new ApiError("Enter their name", 400);
        let user = db.users.find((u) => u.email.toLowerCase() === email);
        if (!user) {
          user = { id: newId("user"), name: input.name.trim(), email, password: DEMO_PASSWORD };
          db.users.push(user);
        }
        if (db.memberships.some((m) => m.userId === user!.id && m.restaurantId === rid)) throw new ApiError("They are already on the staff list", 409);
        const row: MembershipRow = { userId: user.id, restaurantId: rid, role: input.role, pin: null, active: true, invited: true };
        db.memberships.push(row);
        return staffMember(row);
      }),

    saveStaff: (rid, userId, changes) =>
      call(() => {
        const { member: actor } = scope(rid, "staff");
        const row = db.memberships.find((m) => m.restaurantId === rid && m.userId === userId);
        if (!row) throw new ApiError("Staff member not found", 404);
        if (!canManage(actor.role, row.role)) throw new ApiError("Your role can't change this person", 403);
        if (changes.role && !assignableRoles(actor.role).includes(changes.role)) throw new ApiError("Your role can't give that role", 403);
        const owners = db.memberships.filter((m) => m.restaurantId === rid && m.role === "owner" && m.active);
        const losesOwner = row.role === "owner" && ((changes.role && changes.role !== "owner") || changes.active === false);
        if (losesOwner && owners.length === 1) throw new ApiError("A restaurant needs at least one active owner", 409);
        if (changes.pin !== undefined && changes.pin !== null) {
          if (!/^\d{4}$/.test(changes.pin)) throw new ApiError("A PIN is 4 digits", 400);
          if (db.memberships.some((m) => m.restaurantId === rid && m.userId !== userId && m.pin === changes.pin)) {
            throw new ApiError("Someone else here already uses that PIN", 409);
          }
        }
        if (changes.name !== undefined) {
          if (!changes.name.trim()) throw new ApiError("Enter their name", 400);
          db.users.find((u) => u.id === userId)!.name = changes.name.trim();
        }
        if (changes.role) row.role = changes.role;
        if (changes.active !== undefined) row.active = changes.active;
        if (changes.pin !== undefined) row.pin = changes.pin;
        return staffMember(row);
      }),
  };
  return api;
}
