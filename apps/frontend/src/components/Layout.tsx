import { useMemo, useState, type ReactNode } from "react";
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate, useParams } from "react-router";
import { ApiError } from "../data/api";
import { api } from "../data/client";
import { useLoad } from "../data/hooks";
import { makeCan, RestaurantContext, useRestaurant, useSession, type RestaurantValue } from "../data/session";
import { ROLE_LABEL, type Permission } from "../domain/permissions";

const TABS: { to: string; label: string; permission: Permission }[] = [
  { to: "order", label: "New order", permission: "order" },
  { to: "orders", label: "Orders", permission: "orders" },
  { to: "kitchen", label: "Kitchen", permission: "kitchen" },
  { to: "menu", label: "Menu", permission: "soldOut" },
  { to: "staff", label: "Staff", permission: "staff" },
  { to: "settings", label: "Settings", permission: "settings" },
];

/** The frame around every restaurant page: loads the restaurant and shows the nav for this role. */
export function Layout() {
  const { rid = "" } = useParams();
  const { data, error, reload } = useLoad(() => api.restaurant(rid), [rid]);
  const value = useMemo<RestaurantValue | null>(
    () => (data ? { rid, restaurant: data.restaurant, me: data.me, can: makeCan(data.me), reload } : null),
    [data, rid, reload],
  );

  if (error instanceof ApiError && error.status === 401) return <Navigate to="/login" replace />;
  if (error) {
    return (
      <div className="splash">
        <p>{error.message}</p>
        <Link className="link" to="/restaurants">
          Choose a restaurant
        </Link>
      </div>
    );
  }
  if (!value) return <div className="splash">Loading…</div>;

  return (
    <RestaurantContext.Provider value={value}>
      <div className="app">
        <TopBar />
        <Outlet />
      </div>
    </RestaurantContext.Provider>
  );
}

function TopBar() {
  const { rid, restaurant, me, can } = useRestaurant();
  const { session, logout } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const several = (session?.restaurants.length ?? 0) > 1;

  return (
    <header className="topbar">
      <nav className="tabs">
        {TABS.filter((t) => can(t.permission)).map((t) => (
          <NavLink key={t.to} to={`/r/${rid}/${t.to}`} className={({ isActive }) => (isActive ? "tab active" : "tab")}>
            {t.label}
          </NavLink>
        ))}
      </nav>
      <div className="who">
        <button className="who-button" onClick={() => setOpen(!open)} aria-expanded={open}>
          <span className="who-restaurant">{restaurant.name}</span>
          <span className="who-person">
            {me.name} · {ROLE_LABEL[me.role]}
          </span>
        </button>
        {open && (
          <>
            <div className="menu-backdrop" onClick={() => setOpen(false)} />
            <div className="popover" role="menu">
              <button role="menuitem" onClick={() => navigate(`/r/${rid}/lock?next=${encodeURIComponent(location.pathname)}`)}>
                Switch staff (PIN)
              </button>
              {several && (
                <button role="menuitem" onClick={() => navigate("/restaurants")}>
                  Switch restaurant
                </button>
              )}
              <button
                role="menuitem"
                onClick={async () => {
                  await logout();
                  navigate("/login");
                }}
              >
                Sign out
              </button>
            </div>
          </>
        )}
      </div>
    </header>
  );
}

/** Shows the page only to roles allowed to use it; typing the address doesn't get around it. */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const { can, me, rid } = useRestaurant();
  if (can(permission)) return <>{children}</>;
  return (
    <div className="splash">
      <p>A {ROLE_LABEL[me.role].toLowerCase()} can't open this page. Ask a manager, or switch staff with a PIN.</p>
      <Link className="link" to={`/r/${rid}/order`}>
        Back to New order
      </Link>
    </div>
  );
}
