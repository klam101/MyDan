import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMockApi, type MockApi } from "../src/data/mockApi";
import type { NewOrder } from "../src/domain/types";

let api: MockApi;
let rid: string;

async function signIn(email: string) {
  const session = await api.login(email, "demo");
  return session.restaurants.find((r) => r.name === "Chinatown Kitchen")!.id;
}

/** A Lo Mein order with modifications, built from the seeded menu. */
async function loMeinOrder(): Promise<NewOrder> {
  const menu = await api.menu(rid);
  const item = menu.categories.flatMap((c) => c.items).find((i) => i.code === "26")!;
  const mods = menu.modifierLists.flatMap((l) => l.modifiers);
  return {
    type: "walk_in",
    lines: [
      {
        menuItemId: item.id,
        quantity: 1,
        optionIds: [item.optionGroups[0].options[1].id],
        modifiers: [
          { action: "no", modifierId: mods.find((m) => m.name === "Onion")!.id },
          { action: "add", modifierId: mods.find((m) => m.name === "Shrimp")!.id },
        ],
      },
    ],
  };
}

beforeEach(async () => {
  api = createMockApi({ latency: 0, printDelay: 10, persist: false });
  rid = await signIn("cashier@demo.test");
});

describe("mock API", () => {
  it("numbers today's orders after the seeded ones and prints a ticket", async () => {
    const before = await api.orders(rid);
    const order = await api.createOrder(rid, await loMeinOrder());
    expect(order.number).toBe(Math.max(...before.map((o) => o.number)) + 1);
    expect(order.createdBy.name).toBe("Tony Wu");
    expect(order.printJobs[0].status).toBe("queued");
    expect(order.printJobs[0].ticket.map((l) => l.text)).toContain("   NO ONION 洋蔥");
    await vi.waitFor(async () => expect((await api.order(rid, order.id)).printJobs[0].status).toBe("printed"));
  });

  it("keeps the order when the printer is offline", async () => {
    await api.switchStaff(rid, "1111");
    await api.setPrinterStatus(rid, "offline");
    const order = await api.createOrder(rid, await loMeinOrder());
    await vi.waitFor(async () => expect((await api.order(rid, order.id)).printJobs[0].status).toBe("failed"));
    expect((await api.orders(rid)).some((o) => o.id === order.id)).toBe(true);
  });

  it("prints a CHANGED ticket when an order is edited", async () => {
    const input = await loMeinOrder();
    const order = await api.createOrder(rid, input);
    const changed = await api.updateOrder(rid, order.id, {
      ...input,
      lines: [{ ...input.lines[0], lineId: order.lines[0].id, quantity: 2 }],
    });
    expect(changed.version).toBe(2);
    expect(changed.printJobs.at(-1)!.kind).toBe("changed");
    // Chinatown Kitchen prints both languages, so the Chinese name follows.
    expect(changed.printJobs.at(-1)!.ticket.map((l) => l.text)).toContain("* 2 x 26 Vegetable Lo Mein 素菜撈麵");
  });

  it("rejects a phone order with no name or number", async () => {
    await expect(api.createOrder(rid, { ...(await loMeinOrder()), type: "phone" })).rejects.toMatchObject({ status: 400 });
  });

  it("stops cashiers from editing the menu but lets them mark dishes sold out", async () => {
    const menu = await api.menu(rid);
    const dish = menu.categories[0].items[0];
    await expect(api.saveItem(rid, dish.id, { name: "X" })).rejects.toMatchObject({ status: 403 });
    expect((await api.setItemAvailable(rid, dish.id, false)).available).toBe(false);
    await expect(api.staff(rid)).rejects.toMatchObject({ status: 403 });
  });

  it("switches who is taking orders with a PIN", async () => {
    const manager = await api.switchStaff(rid, "2222");
    expect(manager.role).toBe("manager");
    expect((await api.staff(rid)).length).toBeGreaterThan(0);
    await expect(api.switchStaff(rid, "9999")).rejects.toMatchObject({ status: 401 });
  });

  it("keeps each restaurant's menu and orders apart", async () => {
    await api.logout();
    const session = await api.login("owner@demo.test", "demo");
    expect(session.restaurants).toHaveLength(2);
    const other = session.restaurants.find((r) => r.name === "Lucky Garden")!.id;
    expect(await api.orders(other)).toEqual([]);
    const ourItem = (await api.menu(rid)).categories[0].items[0];
    await expect(api.saveItem(other, ourItem.id, { name: "X" })).rejects.toMatchObject({ status: 404 });
    expect((await api.menu(other)).categories.map((c) => c.name)).toEqual(["Appetizers", "Soup", "Fried Rice", "Lo Mein"]);
  });

  it("detaches a removed modifier list from categories and dishes", async () => {
    await api.switchStaff(rid, "1111");
    const veg = (await api.menu(rid)).modifierLists.find((l) => l.name === "Vegetables")!;
    await api.removeModifierList(rid, veg.id);
    const after = await api.menu(rid);
    expect(after.categories.some((c) => c.modifierListIds.includes(veg.id))).toBe(false);
  });

  it("refuses to remove the last owner", async () => {
    await api.switchStaff(rid, "1111");
    const owner = (await api.staff(rid)).find((s) => s.role === "owner")!;
    await expect(api.saveStaff(rid, owner.userId, { role: "manager" })).rejects.toMatchObject({ status: 409 });
  });
});
