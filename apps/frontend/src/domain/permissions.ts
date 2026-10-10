import type { Role } from "./types";

export type Permission = "order" | "orders" | "kitchen" | "soldOut" | "menu" | "staff" | "settings";

const BY_ROLE: Record<Role, Permission[]> = {
  owner: ["order", "orders", "kitchen", "soldOut", "menu", "staff", "settings"],
  manager: ["order", "orders", "kitchen", "soldOut", "menu", "staff"],
  cashier: ["order", "orders", "kitchen", "soldOut"],
};

export const can = (role: Role, permission: Permission) => BY_ROLE[role].includes(permission);

/** Owners manage everyone; managers manage cashiers only. */
export const canManage = (actor: Role, target: Role) => actor === "owner" || (actor === "manager" && target === "cashier");

/** Roles an actor may give to someone else. */
export const assignableRoles = (actor: Role): Role[] =>
  actor === "owner" ? ["owner", "manager", "cashier"] : actor === "manager" ? ["cashier"] : [];

export const ROLE_LABEL: Record<Role, string> = { owner: "Owner", manager: "Manager", cashier: "Cashier" };
