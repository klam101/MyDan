// The modifier lists and choice groups a new restaurant starts with, and which categories they
// attach to. Read from the family restaurant's register (Front) on 2026-10-21.
// No runtime imports: apps/android/scripts/export-starter-menu.mjs reads this file with Node.
import type { PreModifier } from "../domain/types";

export type ListSeed = [
  name: string,
  actions: PreModifier[],
  modifiers: [name: string, altName: string, price: number][],
  altName?: string,
];

/** Front's Special tab: the cashier taps an action (Add, More, Less…), then the ingredient. */
export const STARTER_LISTS: ListSeed[] = [
  [
    "Meat",
    ["add", "extra", "no", "only", "sub"],
    [
      ["Egg", "蛋", 1.75],
      ["Shrimp", "蝦", 1.75],
      ["Beef", "牛肉", 1.75],
      ["Chicken", "雞肉", 1.5],
      ["Roast Pork", "叉燒", 1.5],
      ["Jumbo Shrimp", "大蝦", 1.25],
      ["Scallops", "干貝", 1],
    ],
    "肉類",
  ],
  [
    "Cook",
    ["extra", "light", "no", "only"],
    [
      ["Crispy", "酥脆", 0],
      ["Fried", "炸", 0],
      ["Well Done", "全熟", 0],
      ["Steamed", "蒸", 0],
      ["Fried Hard", "炸脆", 0],
      ["Not Fried Hot", "不要炸脆", 0],
    ],
    "烹飪",
  ],
  [
    "Vegetables",
    ["add", "extra", "light", "no", "only", "sub", "side"],
    [
      ["Mixed Veg", "雜菜", 1.5],
      ["Onion", "洋蔥", 1],
      ["Broccoli", "芥蘭", 1],
      ["Mushroom", "蘑菇", 1],
      ["Baby Corn", "小玉米", 1],
      ["Green Pepper", "青椒", 1],
      ["Scallion", "蔥", 0.5],
      ["Carrot", "紅蘿蔔", 0.5],
      ["Pea Pods", "雪豆", 1],
      ["Bean Sprouts", "芽菜", 0.5],
    ],
    "蔬菜",
  ],
  [
    "Sauce & spice",
    ["add", "extra", "light", "no", "only", "side"],
    [
      ["Sauce", "汁", 0],
      ["Spicy", "辣", 0],
      ["MSG", "味精", 0],
      ["Garlic", "蒜", 0],
      ["Salt", "鹽", 0],
      ["Sugar", "糖", 0],
    ],
    "醬汁",
  ],
];

const MAINS = ["Lunch Special", "Chicken", "Beef", "Seafood", "Pork", "Vegetable", "Diet Dishes", "House Special", "Combination Plates", "Chop Suey", "Egg Foo Young"];
const NOODLES_AND_RICE = ["Fried Rice", "Lo Mein", "Chow Mein", "Chow Mei Fun"];

/** Which categories each list is attached to, by category name. */
export const STARTER_ATTACH: Record<string, string[]> = {
  Meat: [...MAINS, ...NOODLES_AND_RICE],
  Cook: [...MAINS, "American Selections", "Appetizers"],
  Vegetables: [...MAINS, ...NOODLES_AND_RICE],
  "Sauce & spice": [...MAINS, ...NOODLES_AND_RICE, "Sweet & Sour"],
};

export type ChoiceSeed = [
  name: string,
  altName: string,
  min: number,
  max: number,
  choices: [name: string, altName: string, price: number][],
  categories: string[],
];

/** Front's MOD tab: optional priced picks such as the rice that comes with a combo. */
export const STARTER_CHOICES: ChoiceSeed[] = [
  [
    "Comb & Lunch Choice",
    "套餐&特惠午餐米飯選擇",
    0,
    1,
    [
      ["w. White Rice", "跟白米飯", 0],
      ["w. Fried Rice", "跟炒飯", 0],
      ["w. Plain Fried Rice", "跟淨炒飯", 0],
      ["w. Veg Fried Rice", "跟菜炒飯", 0],
      ["w. Chicken Fried Rice", "跟雞炒飯", 1.5],
      ["w. Pork Fried Rice", "跟叉燒炒飯", 1.5],
      ["w. Shrimp Fried Rice", "跟蝦炒飯", 2],
      ["w. Beef Fried Rice", "跟牛炒飯", 2],
      ["w. House Special Fried Rice", "跟本樓炒飯", 4.75],
      ["w. Plain Lo Mein", "跟淨撈麵", 3],
      ["w. Veg Lo Mein", "跟菜撈麵", 4],
      ["w. Chicken Lo Mein", "跟雞撈麵", 4],
      ["w. Pork Lo Mein", "跟叉燒撈麵", 4],
      ["w. Beef Lo Mein", "跟牛撈麵", 4.75],
      ["w. Shrimp Lo Mein", "跟蝦撈麵", 4.75],
      ["w. House Lo Mein", "跟本樓撈麵", 5.75],
      ["No Rice", "不要飯", 0],
      ["YFR", "揚州炒飯", 4.75],
      ["Lo Mein", "撈麵", 3],
    ],
    ["Lunch Special", "Combination Plates"],
  ],
  [
    "Something Extra",
    "加菜",
    0,
    5,
    [
      ["Extra Shrimp", "多蝦", 1],
      ["Extra Beef", "多牛", 1],
      ["Extra Chicken", "多雞", 1],
      ["Extra Pork", "多肉", 1],
      ["Extra Veg", "多菜", 1],
    ],
    ["Lunch Special", "Combination Plates"],
  ],
];
