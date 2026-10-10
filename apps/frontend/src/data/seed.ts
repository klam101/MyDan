// Demo data for the mock API: two restaurants, three staff logins, shared modifier lists and
// a day of orders. The family restaurant's real menu comes from the local server's menu file.
import { MENU, type MenuSection } from "../../../server/src/menu-data";
import type { MenuOptionGroup, ModifierList, NewOrderLine, Order, PreModifier, Restaurant } from "../domain/types";
import { composeOrder, newId, priceModel, printJob, businessDate, type MockDb } from "./store";

export const DEMO_PASSWORD = "demo";

/** Shown on the login screen so anyone trying the demo can sign in. */
export const DEMO_LOGINS = [
  { email: "owner@demo.test", label: "Owner", pin: "1111" },
  { email: "manager@demo.test", label: "Manager", pin: "2222" },
  { email: "cashier@demo.test", label: "Cashier", pin: "3333" },
];

const cents = (dollars: number) => Math.round(dollars * 100);

function addMenu(db: MockDb, rid: string, sections: MenuSection[]): void {
  sections.forEach((section, s) => {
    const categoryId = newId("cat");
    db.categories.push({ id: categoryId, restaurantId: rid, name: section.name, note: section.note ?? null, modifierListIds: [], sortOrder: s });
    section.rows.forEach(([code, altName, name, prices, extras = {}], r) => {
      const labels = extras.sizes ?? section.sizes ?? [];
      const sizes = Array.isArray(prices)
        ? prices.flatMap((price, k) => (price === null ? [] : [{ name: labels[k], priceCents: cents(price) }]))
        : [];
      const model = priceModel(Array.isArray(prices) ? undefined : cents(prices), sizes);
      if (typeof model === "string") throw new Error(`Menu item ${code} ${name}: ${model}`);
      const optionGroups: MenuOptionGroup[] = [];
      if (model.sizes.length) optionGroups.push({ name: "Size", options: model.sizes.map((o) => ({ id: newId("opt"), ...o })) });
      if (extras.choices?.length) {
        optionGroups.push({ name: "Choice", options: extras.choices.map((c) => ({ id: newId("opt"), name: c, extraCents: 0 })) });
      }
      db.items.push({
        id: newId("item"),
        restaurantId: rid,
        categoryId,
        code,
        name,
        altName,
        priceCents: model.base,
        spicy: extras.spicy ?? false,
        available: true,
        optionGroups,
        modifierListIds: [],
        sortOrder: s * 1000 + r,
      });
    });
  });
}

type ListSeed = [name: string, actions: PreModifier[], modifiers: [name: string, altName: string, price: number][]];

const LISTS: ListSeed[] = [
  [
    "Vegetables",
    ["no", "extra", "add"],
    [
      ["Onion", "洋蔥", 1],
      ["Scallion", "蔥", 0.5],
      ["Broccoli", "芥蘭", 1],
      ["Carrot", "紅蘿蔔", 0.5],
      ["Pea Pods", "雪豆", 1],
      ["Mushroom", "蘑菇", 1],
      ["Bean Sprouts", "芽菜", 0.5],
      ["Peppers", "青椒", 1],
    ],
  ],
  [
    "Sauce & spice",
    ["no", "light", "extra", "side"],
    [
      ["Sauce", "汁", 0],
      ["Spicy", "辣", 0],
      ["MSG", "味精", 0],
      ["Garlic", "蒜", 0],
      ["Salt", "鹽", 0],
      ["Sugar", "糖", 0],
    ],
  ],
  [
    "Add protein",
    ["add", "sub"],
    [
      ["Chicken", "雞", 2],
      ["Roast Pork", "叉燒", 2],
      ["Beef", "牛", 2.5],
      ["Shrimp", "蝦", 2.5],
    ],
  ],
];

/** Which categories each list is attached to, by category name. */
const ATTACH: Record<string, string[]> = {
  Vegetables: ["Lunch Special", "Chicken", "Beef", "Seafood", "Pork", "Vegetable", "Diet Dishes", "House Special", "Lo Mein", "Chow Mein", "Fried Rice"],
  "Sauce & spice": ["Lunch Special", "Chicken", "Beef", "Seafood", "Pork", "Vegetable", "Diet Dishes", "House Special", "Lo Mein", "Chow Mein", "Chow Mei Fun", "Sweet & Sour"],
  "Add protein": ["Fried Rice", "Lo Mein", "Chow Mein", "Chow Mei Fun"],
};

function addModifierLists(db: MockDb, rid: string): void {
  for (const [name, actions, modifiers] of LISTS) {
    const list: ModifierList = {
      id: newId("mlist"),
      name,
      actions,
      modifiers: modifiers.map(([n, alt, price]) => ({ id: newId("mod"), name: n, altName: alt, priceCents: cents(price) })),
    };
    db.modifierLists.push({ ...list, restaurantId: rid });
    for (const category of db.categories) {
      if (category.restaurantId === rid && ATTACH[name].includes(category.name)) category.modifierListIds.push(list.id);
    }
  }
}

/** A dish by its menu number, with options and modifications picked by name. */
function line(db: MockDb, rid: string, code: string, quantity: number, options: string[] = [], mods: [PreModifier, string][] = [], note?: string): NewOrderLine {
  const item = db.items.find((i) => i.restaurantId === rid && i.code === code)!;
  const allMods = db.modifierLists.filter((l) => l.restaurantId === rid).flatMap((l) => l.modifiers);
  return {
    menuItemId: item.id,
    quantity,
    optionIds: item.optionGroups.flatMap((g) => g.options.filter((o) => options.includes(o.name)).map((o) => o.id)),
    modifiers: mods.map(([action, name]) => ({ action, modifierId: allMods.find((m) => m.name === name)!.id })),
    note,
  };
}

function addOrders(db: MockDb, restaurant: Restaurant, now: Date, cashier: { userId: string; name: string }): void {
  const rid = restaurant.id;
  const ago = (minutes: number) => new Date(now.getTime() - minutes * 60_000);
  const L = (code: string, qty: number, opts?: string[], mods?: [PreModifier, string][], note?: string) => line(db, rid, code, qty, opts, mods, note);

  const seeds: { minutes: number; status: Order["status"]; readyMinutes?: number; input: Parameters<typeof composeOrder>[2] }[] = [
    { minutes: 95, status: "picked_up", readyMinutes: 82, input: { type: "walk_in", lines: [L("L6", 1, [], [["extra", "Spicy"]]), L("1", 2)] } },
    {
      minutes: 80,
      status: "picked_up",
      readyMinutes: 64,
      input: { type: "phone", customerName: "Mrs. Wong", customerPhone: "917-555-0142", lines: [L("19", 1, ["Qt", "Chicken"], [["add", "Shrimp"]]), L("10", 1, ["Qt"])] },
    },
    { minutes: 61, status: "cancelled", input: { type: "walk_in", lines: [L("33", 1, ["Pt"])] } },
    {
      minutes: 41,
      status: "ready",
      readyMinutes: 26,
      input: { type: "phone", customerName: "Danny", customerPhone: "646-555-0198", lines: [L("24", 2, ["Qt", "Roast Pork"], [["no", "Bean Sprouts"]]), L("3", 1)] },
    },
    { minutes: 33, status: "ready", readyMinutes: 20, input: { type: "walk_in", lines: [L("L3", 1, ["Chicken"], [["no", "Onion"], ["side", "Sauce"]])] } },
    {
      minutes: 27,
      status: "open",
      input: { type: "phone", customerName: "Kim", customerPhone: "718-555-0111", note: "Extra duck sauce", lines: [L("36", 1, ["Qt"], [["extra", "Spicy"], ["no", "Onion"]]), L("21", 1, ["Pt"]), L("2", 3)] },
    },
    { minutes: 17, status: "open", input: { type: "walk_in", lines: [L("33", 1, ["Qt"], [["light", "Sauce"], ["no", "MSG"]]), L("15", 1, ["Pt"])] } },
    {
      minutes: 6,
      status: "open",
      input: {
        type: "phone",
        customerName: "Ms. Patel",
        customerPhone: "347-555-0170",
        pickupAt: new Date(now.getTime() + 15 * 60_000).toISOString(),
        lines: [L("26", 1, ["Qt"], [["no", "Mushroom"], ["add", "Broccoli"]], "Cut noodles short"), L("5", 1, ["Steamed"])],
      },
    },
  ];

  seeds.forEach((seed, i) => {
    const createdAt = ago(seed.minutes);
    const order: Order = {
      id: newId("ord"),
      restaurantId: rid,
      number: i + 1,
      businessDate: businessDate(now, restaurant.timezone),
      status: seed.status,
      ...composeOrder(db, rid, seed.input),
      createdAt: createdAt.toISOString(),
      updatedAt: createdAt.toISOString(),
      readyAt: seed.readyMinutes ? ago(seed.readyMinutes).toISOString() : null,
      createdBy: cashier,
      version: 1,
      printJobs: [],
    };
    order.printJobs.push({ ...printJob(restaurant, order, "new", createdAt), status: "printed" });
    if (seed.status === "cancelled") order.printJobs.push({ ...printJob(restaurant, order, "cancelled", ago(seed.minutes - 3)), status: "printed" });
    db.orders.push(order);
  });
}

export function seedDb(now = new Date()): MockDb {
  const db: MockDb = { users: [], memberships: [], restaurants: [], categories: [], items: [], modifierLists: [], orders: [] };

  const user = (name: string, email: string) => {
    const u = { id: newId("user"), name, email, password: DEMO_PASSWORD };
    db.users.push(u);
    return u;
  };
  const owner = user("Alex Chen", "owner@demo.test");
  const manager = user("Mei Lin", "manager@demo.test");
  const cashier = user("Tony Wu", "cashier@demo.test");
  const cashier2 = user("Sam Lee", "sam@demo.test");

  const main: Restaurant = {
    id: newId("rest"),
    name: "Chinatown Kitchen",
    phone: "718-555-0100",
    address: "12 Main St",
    taxRate: 0.08875,
    timezone: "America/New_York",
    ticketLanguage: "both",
    printer: { kind: "epson_sdp", name: "Kitchen", deviceId: "X4TM000123", status: "online" },
  };
  const second: Restaurant = {
    id: newId("rest"),
    name: "Lucky Garden",
    phone: "718-555-0200",
    address: "88 Bay Ave",
    taxRate: 0.08875,
    timezone: "America/New_York",
    ticketLanguage: "en",
    printer: { kind: "none", name: "", deviceId: "", status: "online" },
  };
  db.restaurants.push(main, second);

  const member = (userId: string, restaurantId: string, role: "owner" | "manager" | "cashier", pin: string | null) =>
    db.memberships.push({ userId, restaurantId, role, pin, active: true, invited: false });
  member(owner.id, main.id, "owner", "1111");
  member(manager.id, main.id, "manager", "2222");
  member(cashier.id, main.id, "cashier", "3333");
  member(owner.id, second.id, "owner", "1111");
  member(cashier2.id, second.id, "cashier", "4444");

  addMenu(db, main.id, MENU);
  addModifierLists(db, main.id);
  addOrders(db, main, now, { userId: cashier.id, name: cashier.name });

  // The second restaurant is smaller, to show that menus and orders stay separate.
  addMenu(db, second.id, MENU.filter((s) => ["Appetizers", "Soup", "Fried Rice", "Lo Mein"].includes(s.name)));
  addModifierLists(db, second.id);
  return db;
}
