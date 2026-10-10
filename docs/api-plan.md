# Cloud API plan

The cloud frontend (`apps/frontend`) runs on a mock API today. This is the list of calls its
screens make, and the HTTP endpoints the real backend should provide for them. The contract is
the `Api` interface in `apps/frontend/src/data/api.ts` and the types in
`apps/frontend/src/domain/types.ts`. The mock in `apps/frontend/src/data/mockApi.ts` already
enforces the rules below, so it doubles as a reference implementation.

All restaurant routes sit under `/api/restaurants/:rid`. Every request carries the hosted-auth
session token. The server finds the caller's membership in that restaurant and checks the role.
A restaurant the caller doesn't belong to returns 404, not 403, so its existence isn't revealed.
Errors return `{ error: "message for the cashier" }` with status 400 (bad input), 401 (signed out),
403 (role not allowed), 404 or 409 (conflict).

## Endpoints

| `Api` method | Endpoint | Who | Notes |
| --- | --- | --- | --- |
| `login`, `logout` | Hosted auth (Supabase Auth or Clerk) | anyone | Not our API; the frontend swaps the demo form for the provider's sign-in. |
| `session` | `GET /api/me` | signed in | User plus `restaurants: { id, name, role }[]` for active memberships. |
| `restaurant` | `GET /r/:rid` | member | Restaurant plus `me`: the staff member acting on this device. |
| `switchStaff` | `POST /r/:rid/staff-session` `{ pin }` | member | Shared tablet: sets who takes orders on this device. Roles are checked against this person. Rate-limit wrong PINs. |
| `saveSettings` | `PATCH /r/:rid/settings` | owner | Name, phone, address, tax rate, time zone, ticket language, printer kind/name/device id. |
| `printTestTicket` | `POST /r/:rid/printer/test` | owner | Queues a test ticket. |
| `setPrinterStatus` | (none) | — | Demo only. The real status is "last seen" from the printer's polling. |
| `menu` | `GET /r/:rid/menu` | member | Categories with dishes, plus modifier lists. Removed dishes are left out. |
| `addCategory` | `POST /r/:rid/menu/categories` | manager+ | |
| `saveCategory` | `PATCH /r/:rid/menu/categories/:id` | manager+ | Includes `modifierListIds`. |
| `removeCategory` | `DELETE /r/:rid/menu/categories/:id` | manager+ | 409 while it still has dishes. |
| `addItem` | `POST /r/:rid/menu/items` | manager+ | Sizes are sent as full prices; the server stores the cheapest as the base price. |
| `saveItem` | `PATCH /r/:rid/menu/items/:id` | manager+ | Keeps option ids for sizes and choices that stay, so open carts and past orders still match. |
| `removeItem` | `DELETE /r/:rid/menu/items/:id` | manager+ | Archive the dish, don't delete it; past orders refer to it. |
| `setItemAvailable` | `PUT /r/:rid/menu/items/:id/available` `{ available }` | member | Cashiers may mark dishes sold out. |
| `addModifierList` | `POST /r/:rid/modifier-lists` | manager+ | Name must be unique in the restaurant. |
| `saveModifierList` | `PUT /r/:rid/modifier-lists/:id` | manager+ | Modifiers that keep their `id` stay linked. |
| `removeModifierList` | `DELETE /r/:rid/modifier-lists/:id` | manager+ | Also detaches it from categories and dishes. |
| `orders` | `GET /r/:rid/orders?date=today` | member | Today in the restaurant's time zone, newest first. |
| `order` | `GET /r/:rid/orders/:id` | member | Includes `printJobs` with each ticket as printed. |
| `createOrder` | `POST /r/:rid/orders` | member | Saves first, then queues a NEW ticket. Never fails because of the printer. |
| `updateOrder` | `PUT /r/:rid/orders/:id` | member | Replaces lines and details, bumps `version`, queues a CHANGED ticket. 409 once picked up or cancelled. |
| `cancelOrder` | `POST /r/:rid/orders/:id/cancel` | member | Queues a CANCELLED ticket. |
| `reprint` | `POST /r/:rid/orders/:id/reprint` | member | Queues a REPRINT (or CANCELLED again). |
| `setOrderStatus` | `PUT /r/:rid/orders/:id/status` `{ status }` | member | `open`, `ready` or `picked_up`; sets `readyAt`. |
| `staff` | `GET /r/:rid/staff` | manager+ | |
| `inviteStaff` | `POST /r/:rid/staff` `{ name, email, role }` | manager+ | Sends an invite email. Managers may only add cashiers. |
| `saveStaff` | `PATCH /r/:rid/staff/:userId` | manager+ | Role, active, name and PIN. Managers only change cashiers. A restaurant always keeps one active owner. PINs are unique per restaurant. |

(`/r/:rid` is short for `/api/restaurants/:rid`.)

## Rules the server owns

- **Prices come from the menu, not the tablet.** The tablet sends dish, option and modifier ids
  (`NewOrderLine`). The server checks them and copies names and prices into the order
  (`buildLine` in `apps/frontend/src/domain/lines.ts`). Share that module with the backend
  instead of rewriting it.
- **Modifiers:** a dish offers its category's lists plus its own. Each pick must come from one of
  those lists, with an action that list allows, and each ingredient appears once per dish.
  ADD, EXTRA and SUB charge the modifier's price. NO, LIGHT and ON SIDE are free.
- **Order numbers** restart each business day in the restaurant's time zone. Assign them in a
  transaction, with a unique key on (restaurant, business date, number).
- **Editing an order** keeps each line's `id`, so the CHANGED ticket can mark lines as new (+),
  changed (\*) or removed (-) (`buildTicket` in `apps/frontend/src/domain/ticket.ts`).
- Phone orders need a customer name or phone number.

## Printing

Tickets are stored as print jobs (`queued` → `printed` / `failed`) with the ticket lines frozen
at send time. The Wi-Fi printer fetches them from the cloud. This needs endpoints the screens
never call:

- **Epson Server Direct Print:** the printer polls a URL with its device id. The server answers
  with queued jobs as ePOS-Print XML and marks them printed when the printer reports success.
- **Star CloudPRNT:** the printer polls. The server says a job is ready, the printer downloads it
  and then confirms.

Chinese text needs the printer's GB18030/Big5 mode. Test that on the real printer early.

## Live updates

The Orders and Kitchen screens poll every 5 seconds, and the detail page every 2 seconds while
a ticket is queued. That is fine for one restaurant. Server-sent events per restaurant can
replace polling later without changing the `Api` shape.

## Database sketch (Postgres)

`restaurants` · `users` · `memberships (user_id, restaurant_id, role, pin_hash, active)` ·
`categories` · `menu_items (archived)` · `item_options (group: Size | Choice)` ·
`modifier_lists` · `modifiers` · `category_modifier_lists` · `item_modifier_lists` ·
`orders (unique restaurant_id, business_date, number)` · `order_lines` (snapshot of names,
prices, options and modifiers) · `print_jobs (ticket jsonb, status)`.
Every table except `users` carries `restaurant_id`, and every query filters on it.
