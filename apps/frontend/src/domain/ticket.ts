import { lineChanged } from "./lines";
import { describeModifier } from "./modifiers";
import type { Order, OrderLine, Restaurant, TicketKind, TicketLanguage, TicketLine } from "./types";

/** Characters per line on an 80 mm printer in its normal font. */
export const LINE_WIDTH = 42;
const RULE: TicketLine = { text: "-".repeat(LINE_WIDTH) };

export interface TicketOptions {
  language: TicketLanguage;
  timezone: string;
  /** For a CHANGED ticket: the order as the kitchen last saw it. */
  previous?: Order;
}

const time = (iso: string, timezone: string) =>
  new Date(iso).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit", timeZone: timezone });

function names(name: string, altName: string | null, language: TicketLanguage): string {
  if (language === "zh") return altName || name;
  if (language === "both" && altName) return `${name} ${altName}`;
  return name;
}

function dishLines(line: OrderLine, language: TicketLanguage, mark = ""): TicketLine[] {
  const code = line.code ? `${line.code} ` : "";
  const out: TicketLine[] = [{ text: `${mark}${line.quantity} x ${code}${names(line.name, line.altName, language)}`, style: "tall", bold: true }];
  if (line.options.length) out.push({ text: `   ${line.options.map((o) => o.name).join(" / ").toUpperCase()}`, style: "tall", bold: true });
  for (const m of line.modifiers) {
    const alt = language !== "en" && m.altName ? ` ${m.altName}` : "";
    out.push({ text: `   ${describeModifier(m).toUpperCase()}${alt}`, style: "tall", bold: true });
  }
  if (line.note) out.push({ text: `   >> ${line.note}`, style: "tall" });
  return out;
}

/** Lays out a kitchen ticket. Turning it into printer commands happens in the cloud. */
export function buildTicket(order: Order, kind: TicketKind, opts: TicketOptions): TicketLine[] {
  const lines: TicketLine[] = [];
  if (kind === "cancelled") lines.push({ text: " CANCELLED ", style: "big", bold: true, invert: true });
  if (kind === "changed") lines.push({ text: " CHANGED ", style: "big", bold: true, invert: true });
  if (kind === "reprint") lines.push({ text: "REPRINT", bold: true });

  lines.push({ text: `#${order.number}  ${order.type === "phone" ? "PHONE" : "WALK-IN"}`, style: "big", bold: true });
  lines.push({ text: `${time(order.createdAt, opts.timezone)}  ${order.businessDate}` });
  if (order.pickupAt) lines.push({ text: `PICKUP ${time(order.pickupAt, opts.timezone)}`, style: "tall", bold: true });
  const customer = [order.customerName, order.customerPhone].filter(Boolean).join("  ");
  if (customer) lines.push({ text: customer, bold: true });
  lines.push(RULE);

  const previous = kind === "changed" ? opts.previous : undefined;
  for (const line of order.lines) {
    const before = previous?.lines.find((l) => l.id === line.id);
    const mark = !previous ? "" : !before ? "+ " : lineChanged(before, line) ? "* " : "  ";
    lines.push(...dishLines(line, opts.language, mark));
  }

  if (previous) {
    const removed = previous.lines.filter((l) => !order.lines.some((n) => n.id === l.id));
    if (removed.length) {
      lines.push(RULE, { text: "REMOVED:", style: "tall", bold: true });
      for (const line of removed) lines.push(...dishLines(line, opts.language, "- "));
    }
    lines.push({ text: "+ new   * changed   - removed" });
  }

  lines.push(RULE);
  if (order.note) lines.push({ text: `NOTE: ${order.note}`, style: "tall", bold: true });
  return lines;
}

/** A short ticket that proves the printer is connected. */
export function buildTestTicket(restaurant: Restaurant, now: Date): TicketLine[] {
  return [
    { text: "TEST TICKET", style: "big", bold: true },
    { text: restaurant.name, bold: true },
    { text: `${restaurant.printer.name || "Kitchen printer"}  ${time(now.toISOString(), restaurant.timezone)}` },
    RULE,
    { text: "If you can read this, tickets will print here." },
  ];
}
