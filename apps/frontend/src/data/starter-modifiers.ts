// The modifier lists a new restaurant starts with, and which categories they attach to.
// No runtime imports: apps/android/scripts/export-starter-menu.mjs reads this file with Node.
import type { PreModifier } from "../domain/types";

export type ListSeed = [name: string, actions: PreModifier[], modifiers: [name: string, altName: string, price: number][]];

export const STARTER_LISTS: ListSeed[] = [
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
export const STARTER_ATTACH: Record<string, string[]> = {
  Vegetables: ["Lunch Special", "Chicken", "Beef", "Seafood", "Pork", "Vegetable", "Diet Dishes", "House Special", "Lo Mein", "Chow Mein", "Fried Rice"],
  "Sauce & spice": ["Lunch Special", "Chicken", "Beef", "Seafood", "Pork", "Vegetable", "Diet Dishes", "House Special", "Lo Mein", "Chow Mein", "Chow Mei Fun", "Sweet & Sour"],
  "Add protein": ["Fried Rice", "Lo Mein", "Chow Mein", "Chow Mei Fun"],
};
