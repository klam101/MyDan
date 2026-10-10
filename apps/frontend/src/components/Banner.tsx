import type { ReactNode } from "react";

export type BannerKind = "ok" | "error" | "warn" | "info";

export function Banner({ kind, children, onClose }: { kind: BannerKind; children: ReactNode; onClose?: () => void }) {
  return (
    <div className={`banner ${kind}`} role={kind === "error" ? "alert" : "status"}>
      <span>{children}</span>
      {onClose && (
        <button className="banner-close" onClick={onClose} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  );
}
