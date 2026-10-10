export const money = (cents: number) => `${cents < 0 ? "-" : ""}$${(Math.abs(cents) / 100).toFixed(2)}`;

/** Parses a typed dollar amount ("7.6", "$7.60") into cents; null when it isn't a price. */
export function parseMoney(text: string): number | null {
  const cleaned = text.replace(/[$\s]/g, "");
  if (!/^\d+(\.\d{0,2})?$/.test(cleaned)) return null;
  return Math.round(Number(cleaned) * 100);
}
