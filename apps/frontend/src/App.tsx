import type { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router";
import { Layout, RequirePermission } from "./components/Layout";
import { useSession } from "./data/session";
import type { Permission } from "./domain/permissions";
import { KitchenScreen } from "./screens/KitchenScreen";
import { LoginScreen } from "./screens/LoginScreen";
import { MenuScreen } from "./screens/MenuScreen";
import { ModifiersScreen } from "./screens/ModifiersScreen";
import { OrderDetail } from "./screens/OrderDetail";
import { OrderScreen } from "./screens/OrderScreen";
import { OrdersScreen } from "./screens/OrdersScreen";
import { PinLock } from "./screens/PinLock";
import { RestaurantPicker } from "./screens/RestaurantPicker";
import { SettingsScreen } from "./screens/SettingsScreen";
import { StaffScreen } from "./screens/StaffScreen";

const allow = (permission: Permission, page: ReactNode) => <RequirePermission permission={permission}>{page}</RequirePermission>;

export function App() {
  const { session, loading } = useSession();
  if (loading) return <div className="splash">Loading…</div>;

  if (!session) {
    return (
      <Routes>
        <Route path="/login" element={<LoginScreen />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  const home = session.restaurants.length === 1 ? `/r/${session.restaurants[0].id}` : "/restaurants";
  return (
    <Routes>
      <Route path="/restaurants" element={<RestaurantPicker />} />
      <Route path="/r/:rid/lock" element={<PinLock />} />
      <Route path="/r/:rid" element={<Layout />}>
        <Route index element={<Navigate to="order" replace />} />
        <Route path="order" element={allow("order", <OrderScreen />)} />
        <Route path="orders/:orderId/edit" element={allow("order", <OrderScreen />)} />
        <Route path="orders" element={allow("orders", <OrdersScreen />)} />
        <Route path="orders/:orderId" element={allow("orders", <OrderDetail />)} />
        <Route path="kitchen" element={allow("kitchen", <KitchenScreen />)} />
        {/* Cashiers open the menu to mark dishes sold out; editing stays with managers. */}
        <Route path="menu" element={allow("soldOut", <MenuScreen />)} />
        <Route path="menu/modifiers" element={allow("menu", <ModifiersScreen />)} />
        <Route path="staff" element={allow("staff", <StaffScreen />)} />
        <Route path="settings" element={allow("settings", <SettingsScreen />)} />
        <Route path="*" element={<Navigate to="order" replace />} />
      </Route>
      <Route path="*" element={<Navigate to={home} replace />} />
    </Routes>
  );
}
