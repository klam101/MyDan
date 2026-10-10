import type { TicketLine } from "../domain/types";

/** Shows a kitchen ticket the way the 80 mm printer lays it out. */
export function TicketPreview({ ticket }: { ticket: TicketLine[] }) {
  return (
    <div className="ticket" aria-label="Kitchen ticket">
      {ticket.map((line, i) => (
        <div key={i} className={`ticket-line ${line.style ?? "normal"}${line.bold ? " bold" : ""}${line.invert ? " invert" : ""}`}>
          {line.text || " "}
        </div>
      ))}
    </div>
  );
}
