import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { api } from "../data/client";
import { errorMessage } from "../data/hooks";

const KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"];

/** Shared tablet: the next person enters their 4-digit PIN and takes over. */
export function PinLock() {
  const { rid = "" } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const requested = params.get("next");
  const next = requested?.startsWith(`/r/${rid}/`) ? requested : `/r/${rid}/order`;

  useEffect(() => {
    if (pin.length !== 4) return;
    setChecking(true);
    api.switchStaff(rid, pin).then(
      () => navigate(next, { replace: true }),
      (e) => {
        setError(errorMessage(e));
        setPin("");
        setChecking(false);
      },
    );
  }, [pin, rid, next, navigate]);

  function press(key: string) {
    setError(null);
    if (key === "⌫") setPin((p) => p.slice(0, -1));
    else setPin((p) => (p.length < 4 ? p + key : p));
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (/^\d$/.test(e.key)) press(e.key);
      if (e.key === "Backspace") press("⌫");
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <main className="auth">
      <div className="auth-card pin-card">
        <h1>Enter your PIN</h1>
        <div className="pin-dots" aria-label={`${pin.length} of 4 digits entered`}>
          {[0, 1, 2, 3].map((i) => (
            <span key={i} className={i < pin.length ? "dot filled" : "dot"} />
          ))}
        </div>
        <p className={error ? "pin-error" : "muted"}>{error ?? (checking ? "Checking…" : "Whoever enters their PIN takes the next orders.")}</p>
        <div className="keypad">
          {KEYS.map((k, i) =>
            k ? (
              <button key={i} className="key" onClick={() => press(k)} disabled={checking} aria-label={k === "⌫" ? "Delete" : k}>
                {k}
              </button>
            ) : (
              <span key={i} />
            ),
          )}
        </div>
        <Link className="link" to={next}>
          Cancel
        </Link>
      </div>
    </main>
  );
}
