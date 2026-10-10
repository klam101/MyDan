import { NavLink } from "react-router";
import { useRestaurant } from "../data/session";

/** Switches between the dishes and the shared modification lists. */
export function MenuTabs() {
  const { rid, can } = useRestaurant();
  if (!can("menu")) return null;
  return (
    <nav className="segmented sub-tabs">
      <NavLink end to={`/r/${rid}/menu`} className={({ isActive }) => (isActive ? "active" : "")}>
        Dishes
      </NavLink>
      <NavLink to={`/r/${rid}/menu/modifiers`} className={({ isActive }) => (isActive ? "active" : "")}>
        Modifier lists
      </NavLink>
    </nav>
  );
}
