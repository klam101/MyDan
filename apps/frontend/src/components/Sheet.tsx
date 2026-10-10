import { useEffect, type ReactNode } from "react";

/** A dialog that slides over the screen; tapping outside or Escape closes it. */
export function Sheet({ title, onClose, children, wide }: { title: ReactNode; onClose: () => void; children: ReactNode; wide?: boolean }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className={wide ? "sheet wide" : "sheet"} role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <header className="sheet-head">
          <strong>{title}</strong>
          <button className="close" onClick={onClose} aria-label="Close">
            ×
          </button>
        </header>
        <div className="sheet-body">{children}</div>
      </div>
    </div>
  );
}
