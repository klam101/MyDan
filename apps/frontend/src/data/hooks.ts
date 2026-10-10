import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "./api";

export const errorMessage = (e: unknown) => (e instanceof Error ? e.message : "Something went wrong");

/**
 * Loads data and keeps it fresh: `reload` fetches again, `pollMs` refetches on a timer
 * (the kitchen screen and print status use this until the cloud API pushes updates).
 */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[], pollMs?: number) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<ApiError | Error | null>(null);
  const loadRef = useRef(load);
  loadRef.current = load;

  const reload = useCallback(async () => {
    try {
      setData(await loadRef.current());
      setError(null);
    } catch (e) {
      setError(e as Error);
    }
  }, []);

  useEffect(() => {
    setData(undefined);
    void reload();
    if (!pollMs) return;
    const timer = setInterval(() => void reload(), pollMs);
    return () => clearInterval(timer);
  }, [...deps, pollMs, reload]);

  return { data, error, reload, setData };
}

/** Runs an action, tracking whether it is busy and what went wrong. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = useCallback(async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true);
    setError(null);
    try {
      return await fn();
    } catch (e) {
      setError(errorMessage(e));
      return undefined;
    } finally {
      setBusy(false);
    }
  }, []);
  /** For calls that return nothing: true when it worked. */
  const runOk = useCallback(async (fn: () => Promise<unknown>) => (await run(async () => (await fn(), true))) === true, [run]);
  return { busy, error, setError, run, runOk };
}
