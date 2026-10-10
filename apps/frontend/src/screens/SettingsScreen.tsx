import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { Banner } from "../components/Banner";
import { PRINT_LABEL } from "../components/PrintStatus";
import { TicketPreview } from "../components/TicketPreview";
import { api } from "../data/client";
import { useAction } from "../data/hooks";
import { useRestaurant, useSession } from "../data/session";
import type { PrintJob, PrinterKind, Settings, TicketLanguage } from "../domain/types";

const PRINTERS: { kind: PrinterKind; label: string }[] = [
  { kind: "none", label: "No printer (show tickets on screen only)" },
  { kind: "epson_sdp", label: "Epson, Server Direct Print (e.g. TM-m30III)" },
  { kind: "star_cloudprnt", label: "Star, CloudPRNT (e.g. mC-Print3)" },
];

const LANGUAGES: { value: TicketLanguage; label: string }[] = [
  { value: "en", label: "English" },
  { value: "zh", label: "Chinese" },
  { value: "both", label: "English and Chinese" },
];

const TIMEZONES = ["America/New_York", "America/Chicago", "America/Denver", "America/Phoenix", "America/Los_Angeles", "America/Anchorage", "Pacific/Honolulu"];

/** Restaurant details, tax, ticket language and the cloud printer. Owners only. */
export function SettingsScreen() {
  const { rid, restaurant, reload } = useRestaurant();
  const { refresh } = useSession();
  const navigate = useNavigate();
  const [form, setForm] = useState<Settings>(() => {
    const { name, phone, address, taxRate, timezone, ticketLanguage, printer } = restaurant;
    return { name, phone, address, taxRate, timezone, ticketLanguage, printer: { kind: printer.kind, name: printer.name, deviceId: printer.deviceId } };
  });
  const [taxText, setTaxText] = useState(() => String(+(restaurant.taxRate * 100).toFixed(4)));
  const [notice, setNotice] = useState<string | null>(null);
  const [testJob, setTestJob] = useState<PrintJob | null>(null);
  const action = useAction();

  // The demo printer answers about two seconds after the test ticket is queued.
  useEffect(() => {
    if (testJob?.status !== "queued") return;
    const timer = setTimeout(() => setTestJob((j) => j && { ...j, status: restaurant.printer.status === "online" ? "printed" : "failed" }), 2000);
    return () => clearTimeout(timer);
  }, [testJob, restaurant.printer.status]);

  const set = <K extends keyof Settings>(key: K, value: Settings[K]) => setForm({ ...form, [key]: value });
  const setPrinter = (changes: Partial<Settings["printer"]>) => setForm({ ...form, printer: { ...form.printer, ...changes } });

  async function save() {
    const taxRate = Number(taxText) / 100;
    if (taxText.trim() === "" || !Number.isFinite(taxRate)) return action.setError("Tax rate must be a number, e.g. 8.875");
    if (await action.run(() => api.saveSettings(rid, { ...form, taxRate }))) {
      await reload();
      await refresh();
      setNotice("Settings saved.");
    }
  }

  async function printerStatus(online: boolean) {
    if (await action.run(() => api.setPrinterStatus(rid, online ? "online" : "offline"))) await reload();
  }

  return (
    <main className="page settings">
      <h1>Settings</h1>
      {notice && (
        <Banner kind="ok" onClose={() => setNotice(null)}>
          {notice}
        </Banner>
      )}
      {action.error && <Banner kind="error">{action.error}</Banner>}

      <section className="card">
        <h2>Restaurant</h2>
        <label className="stacked">
          Name
          <input value={form.name} onChange={(e) => set("name", e.target.value)} />
        </label>
        <label className="stacked">
          Phone
          <input value={form.phone} inputMode="tel" onChange={(e) => set("phone", e.target.value)} />
        </label>
        <label className="stacked">
          Address
          <input value={form.address} onChange={(e) => set("address", e.target.value)} />
        </label>
        <div className="row">
          <label className="stacked">
            Sales tax (%)
            <input value={taxText} inputMode="decimal" onChange={(e) => setTaxText(e.target.value)} />
          </label>
          <label className="stacked grow">
            Time zone (when the day's order numbers restart)
            <select value={form.timezone} onChange={(e) => set("timezone", e.target.value)}>
              {TIMEZONES.map((z) => (
                <option key={z}>{z}</option>
              ))}
            </select>
          </label>
        </div>
      </section>

      <section className="card">
        <h2>Kitchen tickets</h2>
        <label className="stacked">
          Dish names on tickets
          <select value={form.ticketLanguage} onChange={(e) => set("ticketLanguage", e.target.value as TicketLanguage)}>
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value}>
                {l.label}
              </option>
            ))}
          </select>
        </label>
        <label className="stacked">
          Printer
          <select value={form.printer.kind} onChange={(e) => setPrinter({ kind: e.target.value as PrinterKind })}>
            {PRINTERS.map((p) => (
              <option key={p.kind} value={p.kind}>
                {p.label}
              </option>
            ))}
          </select>
        </label>
        {form.printer.kind !== "none" && (
          <div className="row">
            <label className="stacked grow">
              Printer name
              <input value={form.printer.name} onChange={(e) => setPrinter({ name: e.target.value })} placeholder="Kitchen" />
            </label>
            <label className="stacked grow">
              Serial number / device ID
              <input value={form.printer.deviceId} onChange={(e) => setPrinter({ deviceId: e.target.value })} />
            </label>
          </div>
        )}
        <p className="muted small">
          The printer connects to the restaurant Wi-Fi and fetches tickets from the cloud every few seconds, so tickets may print a few seconds after Send.
        </p>
        {restaurant.printer.kind !== "none" && (
          <div className="printer-status">
            <span className={restaurant.printer.status === "online" ? "print-printed" : "print-failed"}>
              ● {restaurant.printer.name || "Printer"} is {restaurant.printer.status}
            </span>
            <button className="secondary small" disabled={action.busy} onClick={async () => setTestJob((await action.run(() => api.printTestTicket(rid))) ?? null)}>
              Print test ticket
            </button>
            <button className="link small" disabled={action.busy} onClick={() => printerStatus(restaurant.printer.status !== "online")}>
              Demo: make it {restaurant.printer.status === "online" ? "go offline" : "come back"}
            </button>
          </div>
        )}
        {testJob && (
          <div className="test-ticket">
            <span className={`print-${testJob.status}`}>{PRINT_LABEL[testJob.status]}</span>
            <TicketPreview ticket={testJob.ticket} />
          </div>
        )}
      </section>

      <div className="row end">
        <button className="primary" onClick={save} disabled={action.busy}>
          {action.busy ? "Saving…" : "Save settings"}
        </button>
      </div>

      <section className="card demo-card">
        <h2>Demo data</h2>
        <p className="muted">This build keeps everything in this browser. Starting over brings back the sample restaurants, menu and orders.</p>
        <button
          className="danger"
          onClick={async () => {
            if (!confirm("Start the demo over? Every change you made will be lost.")) return;
            api.resetDemo();
            await refresh();
            navigate("/login");
          }}
        >
          Reset demo data
        </button>
      </section>
    </main>
  );
}
