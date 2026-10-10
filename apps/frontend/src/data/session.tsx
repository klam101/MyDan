import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { Permission } from "../domain/permissions";
import { can as roleCan } from "../domain/permissions";
import type { Restaurant, StaffMember } from "../domain/types";
import type { Session } from "./api";
import { api } from "./client";

interface SessionValue {
  session: Session | null;
  loading: boolean;
  login(email: string, password: string): Promise<Session>;
  logout(): Promise<void>;
  refresh(): Promise<void>;
}

const SessionContext = createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setSession(await api.session());
    setLoading(false);
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const value = useMemo<SessionValue>(
    () => ({
      session,
      loading,
      refresh,
      async login(email, password) {
        const s = await api.login(email, password);
        setSession(s);
        return s;
      },
      async logout() {
        await api.logout();
        setSession(null);
      },
    }),
    [session, loading, refresh],
  );
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error("useSession must be used inside SessionProvider");
  return value;
}

export interface RestaurantValue {
  rid: string;
  restaurant: Restaurant;
  /** Who is working the tablet right now. */
  me: StaffMember;
  can(permission: Permission): boolean;
  /** Fetch the restaurant again, e.g. after settings change or a PIN switch. */
  reload(): Promise<void>;
}

export const RestaurantContext = createContext<RestaurantValue | null>(null);

export function useRestaurant(): RestaurantValue {
  const value = useContext(RestaurantContext);
  if (!value) throw new Error("useRestaurant must be used inside a restaurant page");
  return value;
}

export const makeCan = (me: StaffMember) => (permission: Permission) => roleCan(me.role, permission);
