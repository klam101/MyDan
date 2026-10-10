import { useEffect, useState } from "react";
import { parseMoney } from "../domain/money";

/** A dollar amount field that reports cents, or null while the text isn't a price. */
export function MoneyInput({ cents, onChange, label }: { cents: number | null; onChange: (cents: number | null) => void; label?: string }) {
  const [text, setText] = useState(cents === null ? "" : (cents / 100).toFixed(2));
  useEffect(() => {
    if (cents !== parseMoney(text)) setText(cents === null ? "" : (cents / 100).toFixed(2));
    // Only follow outside changes, not our own typing.
  }, [cents]);
  const invalid = text !== "" && parseMoney(text) === null;
  return (
    <span className="money-input">
      $
      <input
        inputMode="decimal"
        aria-label={label ?? "Price"}
        aria-invalid={invalid}
        className={invalid ? "invalid" : undefined}
        value={text}
        placeholder="0.00"
        onChange={(e) => {
          setText(e.target.value);
          onChange(parseMoney(e.target.value));
        }}
      />
    </span>
  );
}
