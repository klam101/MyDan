import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api } from "../data/client";
import type { Order, PrintJob } from "../domain/types";
import { Banner } from "./Banner";

export const PRINT_LABEL: Record<PrintJob["status"], string> = {
  queued: "Waiting for printer",
  printed: "Printed",
  failed: "Did NOT print",
  no_printer: "No printer set up",
};

/**
 * After Send: follows the order's newest ticket until the cloud printer picks it up,
 * so the cashier knows whether the kitchen has it.
 */
export function SentBanner({ rid, order, onClose }: { rid: string; order: Order; onClose: () => void }) {
  const [job, setJob] = useState(order.printJobs.at(-1));

  useEffect(() => {
    setJob(order.printJobs.at(-1));
    let tries = 0;
    const timer = setInterval(async () => {
      tries += 1;
      const latest = (await api.order(rid, order.id).catch(() => null))?.printJobs.at(-1);
      if (latest) setJob(latest);
      if (!latest || latest.status !== "queued" || tries > 20) clearInterval(timer);
    }, 1000);
    return () => clearInterval(timer);
  }, [rid, order]);

  const link = <Link to={`/r/${rid}/orders/${order.id}`}>#{order.number}</Link>;
  if (job?.status === "failed") {
    return (
      <Banner kind="error" onClose={onClose}>
        Order {link} saved, but the ticket did NOT print. Check the printer, then reprint it from Orders.
      </Banner>
    );
  }
  if (job?.status === "no_printer") {
    return (
      <Banner kind="warn" onClose={onClose}>
        Order {link} saved. No kitchen printer is set up, so tell the kitchen.
      </Banner>
    );
  }
  return (
    <Banner kind={job?.status === "printed" ? "ok" : "info"} onClose={onClose}>
      Order {link} sent. {job?.status === "printed" ? "Ticket printed ✓" : "Ticket queued for the printer…"}
    </Banner>
  );
}
