import { describe, expect, it } from "vitest";
import { buildLine, OrderError } from "../src/domain/lines";
import { checkPicks, describeModifier, listsForItem, togglePick } from "../src/domain/modifiers";
import { can, canManage } from "../src/domain/permissions";
import { linePrice, totals } from "../src/domain/pricing";
import { buildTicket } from "../src/domain/ticket";
import type { Menu, ModifierList, Order } from "../src/domain/types";

const veg: ModifierList = {
  id: "veg",
  name: "Vegetables",
  actions: ["no", "extra", "add"],
  modifiers: [
    { id: "onion", name: "Onion", altName: "洋蔥", priceCents: 100 },
    { id: "broc", name: "Broccoli", altName: null, priceCents: 150 },
  ],
};
const sauce: ModifierList = {
  id: "sauce",
  name: "Sauce & spice",
  actions: ["no", "light", "side"],
  modifiers: [{ id: "sauce1", name: "Sauce", altName: null, priceCents: 0 }],
};
const protein: ModifierList = {
  id: "prot",
  name: "Add protein",
  actions: ["add", "sub"],
  modifiers: [{ id: "shrimp", name: "Shrimp", altName: null, priceCents: 250 }],
};

const menu: Menu = {
  modifierLists: [veg, sauce, protein],
  categories: [
    {
      id: "c1",
      name: "Lo Mein",
      note: null,
      modifierListIds: ["veg", "sauce"],
      items: [
        {
          id: "lm",
          categoryId: "c1",
          code: "24",
          name: "Chicken Lo Mein",
          altName: null,
          priceCents: 565,
          spicy: false,
          available: true,
          optionGroups: [
            {
              name: "Size",
              options: [
                { id: "pt", name: "Pt", extraCents: 0 },
                { id: "qt", name: "Qt", extraCents: 215 },
              ],
            },
          ],
          modifierListIds: ["prot", "veg"],
        },
        {
          id: "so",
          categoryId: "c1",
          code: "99",
          name: "Sold out dish",
          altName: null,
          priceCents: 100,
          spicy: false,
          available: false,
          optionGroups: [],
          modifierListIds: [],
        },
      ],
    },
  ],
};
const [loMein] = menu.categories[0].items;
let n = 0;
const id = () => `l${++n}`;

describe("modifier lists", () => {
  it("offers the category's lists, then the dish's own, each once", () => {
    expect(listsForItem(loMein, menu.categories[0], menu.modifierLists).map((l) => l.id)).toEqual(["veg", "sauce", "prot"]);
  });

  it("toggles a pick off, and replaces a different action on the same ingredient", () => {
    let picks = togglePick([], { action: "no", modifierId: "onion" });
    picks = togglePick(picks, { action: "extra", modifierId: "onion" });
    expect(picks).toEqual([{ action: "extra", modifierId: "onion" }]);
    expect(togglePick(picks, { action: "extra", modifierId: "onion" })).toEqual([]);
  });

  it("rejects an action the list doesn't allow and ingredients from other dishes", () => {
    const lists = [veg, sauce];
    expect(checkPicks([{ action: "light", modifierId: "onion" }], lists)).toMatch(/LESS is not allowed for Onion/);
    expect(checkPicks([{ action: "add", modifierId: "shrimp" }], lists)).toMatch(/not offered/);
    expect(
      checkPicks(
        [
          { action: "no", modifierId: "onion" },
          { action: "side", modifierId: "sauce1" },
        ],
        lists,
      ),
    ).toBeNull();
  });

  it("reads like the kitchen says it", () => {
    expect(describeModifier({ action: "no", name: "Onion" })).toBe("NO Onion");
    expect(describeModifier({ action: "side", name: "Sauce" })).toBe("Sauce ON SIDE");
  });
});

describe("pricing", () => {
  it("charges ADD and MORE, not NO, LESS or ON SIDE", () => {
    const line = buildLine(
      menu,
      {
        menuItemId: "lm",
        quantity: 2,
        optionIds: ["qt"],
        modifiers: [
          { action: "no", modifierId: "onion" },
          { action: "extra", modifierId: "broc" },
          { action: "add", modifierId: "shrimp" },
          { action: "side", modifierId: "sauce1" },
        ],
      },
      id,
    );
    expect(line.modifiers.map((m) => m.priceCents)).toEqual([0, 150, 250, 0]);
    expect(linePrice(line)).toBe((565 + 215 + 150 + 250) * 2);
  });

  it("rounds tax to the cent", () => {
    const line = buildLine(menu, { menuItemId: "lm", quantity: 1, optionIds: ["pt"], modifiers: [] }, id);
    expect(totals([line], 0.08875)).toEqual({ subtotalCents: 565, taxCents: 50, totalCents: 615 });
  });

  it("refuses sold-out dishes, missing sizes and bad picks", () => {
    expect(() => buildLine(menu, { menuItemId: "so", quantity: 1, optionIds: [], modifiers: [] }, id)).toThrow(/sold out/);
    expect(() => buildLine(menu, { menuItemId: "lm", quantity: 1, optionIds: [], modifiers: [] }, id)).toThrow(/Pick one size/);
    expect(() =>
      buildLine(menu, { menuItemId: "lm", quantity: 1, optionIds: ["pt"], modifiers: [{ action: "light", modifierId: "onion" }] }, id),
    ).toThrow(OrderError);
  });
});

describe("tickets", () => {
  const order = (lines: Order["lines"]): Order => ({
    id: "o1",
    restaurantId: "r1",
    number: 7,
    businessDate: "2026-10-09",
    type: "phone",
    status: "open",
    customerName: "Kim",
    customerPhone: null,
    pickupAt: null,
    note: null,
    lines,
    subtotalCents: 0,
    taxCents: 0,
    totalCents: 0,
    createdAt: "2026-10-09T22:00:00.000Z",
    updatedAt: "2026-10-09T22:00:00.000Z",
    readyAt: null,
    createdBy: { userId: "u", name: "Tony" },
    version: 1,
    printJobs: [],
  });
  const a = buildLine(menu, { menuItemId: "lm", quantity: 1, optionIds: ["qt"], modifiers: [{ action: "no", modifierId: "onion" }] }, () => "a");
  const b = buildLine(menu, { menuItemId: "lm", quantity: 1, optionIds: ["pt"], modifiers: [] }, () => "b");
  const opts = { language: "both" as const, timezone: "America/New_York" };

  it("prints modifications in capitals under the dish, with the Chinese name", () => {
    const text = buildTicket(order([a]), "new", opts).map((l) => l.text);
    expect(text).toContain("1 x 24 Chicken Lo Mein");
    expect(text).toContain("   QT");
    expect(text).toContain("   NO ONION 洋蔥");
  });

  it("marks added, changed and removed dishes on a CHANGED ticket", () => {
    const before = order([a, b]);
    const c = buildLine(menu, { menuItemId: "lm", quantity: 3, optionIds: ["pt"], modifiers: [] }, () => "c");
    const after = order([{ ...a, quantity: 2 }, c]);
    const text = buildTicket(after, "changed", { ...opts, previous: before }).map((l) => l.text);
    expect(text[0]).toContain("CHANGED");
    expect(text).toContain("* 2 x 24 Chicken Lo Mein");
    expect(text).toContain("+ 3 x 24 Chicken Lo Mein");
    expect(text).toContain("REMOVED:");
    expect(text).toContain("- 1 x 24 Chicken Lo Mein");
  });
});

describe("roles", () => {
  it("keeps settings for owners and staff management above cashiers", () => {
    expect(can("cashier", "soldOut")).toBe(true);
    expect(can("cashier", "menu")).toBe(false);
    expect(can("manager", "settings")).toBe(false);
    expect(canManage("manager", "cashier")).toBe(true);
    expect(canManage("manager", "owner")).toBe(false);
  });
});
