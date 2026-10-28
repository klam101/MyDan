// Writes the starter menu (the family restaurant's printed menu) and starter modifier lists as
// JSON for the Android app, so both apps start from the same data.
// Run from the repo root after changing either source file:
//   node apps/android/scripts/export-starter-menu.mjs
import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { MENU } from "../../server/src/menu-data.ts";
import { STARTER_ATTACH, STARTER_LISTS } from "../../frontend/src/data/starter-modifiers.ts";

const out = new URL("../core/src/main/resources/starter-menu.json", import.meta.url);
const data = {
  sections: MENU.map((s) => ({
    name: s.name,
    note: s.note ?? null,
    sizes: s.sizes ?? [],
    rows: s.rows.map(([code, altName, name, prices, extras = {}]) => ({
      code,
      altName,
      name,
      // One number is a single price; a list lines up with the sizes (null = not sold in that size).
      prices: Array.isArray(prices) ? prices : [prices],
      sized: Array.isArray(prices),
      spicy: extras.spicy ?? false,
      choices: extras.choices ?? [],
      sizes: extras.sizes ?? null,
    })),
  })),
  modifierLists: STARTER_LISTS.map(([name, actions, modifiers]) => ({
    name,
    actions,
    modifiers: modifiers.map(([n, altName, price]) => ({ name: n, altName, price })),
    categories: STARTER_ATTACH[name] ?? [],
  })),
};
writeFileSync(out, JSON.stringify(data, null, 1) + "\n");
console.log(`Wrote ${data.sections.reduce((n, s) => n + s.rows.length, 0)} dishes and ${data.modifierLists.length} modifier lists to ${fileURLToPath(out)}`);
