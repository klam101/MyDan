# Requirements & Roadmap

Restaurant ordering and kitchen ticket system, built first for the owner's family Chinese
takeout restaurant and meant to become a product sold to many restaurants (like Toast).

The work items for this roadmap are the user stories in [`backlog.md`](backlog.md).
The original planning doc is the shared Claude Doc
https://claude.ai/code/artifact/d01dc4d9-07c8-490b-a427-590b9b187b53 ; it still describes the
earlier web-app (PWA) plan. Where it and this file disagree about the app platform, this file wins.

## Goal

Replace shouted orders with written kitchen tickets, so no order is forgotten when the
restaurant gets busy. The cashier enters each order on a tablet, the kitchen gets a ticket
(printed, on a screen, or both), and everyone can see which orders are open and which are done.

**The problem today.** The cashier takes the order and payment and shouts the order into the
kitchen. Chefs hold every order in their heads; at rush time items get forgotten or mixed up,
and nobody can tell at a glance which orders are finished.

**What success looks like.** At the busiest hour no order is lost or remembered wrong, the
cashier never walks to the kitchen to explain an order, and a customer asking "is mine ready?"
gets an answer by looking at a screen.

**Long-term goal.** Sell the system to other restaurants. The family restaurant is the first
customer and tests every phase first. Nothing restaurant-specific lives in code: menu, prices,
tax, printers, languages and order types are edited in the app and stored in the database.

**Reference product.** The restaurant runs Front (FrontStation by ZBS POS) on a Sunmi Android
terminal today. We are building a replica with our own twist; `backlog.md` lists every Front
feature we lack as a user story.

## Who uses it and how an order moves

| Person | Device | What they need to do |
| --- | --- | --- |
| Cashier | Front-counter Android tablet | Enter an order fast (menu buttons, sizes, modifiers, notes), pick the order type, send it to the kitchen |
| Chefs | Kitchen printer, later a kitchen screen | See every open order in the order it came in, with clear items and notes; mark it done |
| Counter / packer | Same tablet, later a pickup screen | See which orders are ready and hand them out |
| Owner / manager | Tablet or phone | Edit menu, prices, staff and settings; read reports |

An order's life:

1. **New:** the cashier builds the order and taps Send. It gets a short number (restarts daily).
2. **In the kitchen:** a ticket prints (v1) and/or shows on the kitchen screen (v2).
3. **Ready:** a chef marks it done (v2). The counter sees it.
4. **Picked up:** handed out and closed. It stays in order history.

## Platform decisions

| Piece | Decision |
| --- | --- |
| App | Native **Android app in Kotlin** for Android tablets and POS terminals (e.g. Sunmi). Replaces the earlier React web-app (PWA) plan. The base app is built; orders are not yet. |
| Backend | Cloud: one API and one Postgres database shared by all restaurants. Every row carries its restaurant id. Logins have roles: owner, manager, cashier (chef later). |
| Auth | Hosted auth (e.g. Supabase Auth or Clerk) for owner accounts; 4-digit staff PINs on the shared tablet. |
| Offline | None. The internet is reliable and card payments stop without it anyway. The app shows clearly when it is offline. |
| Printing | 80 mm receipt printer. Options: cloud printing (Epson Server Direct Print or Star CloudPRNT, printer fetches tickets from the cloud) or, now that the app is native Android, printing from the tablet over Wi-Fi/LAN (ESC/POS on port 9100) or a terminal's built-in printer. **Open question:** pick one; see H1 in the backlog. |
| Payments | None until v3. The current register keeps handling them. Card payments become a premium feature. |
| Tests | Kotlin unit tests (JUnit) for logic such as prices, tax and modifiers; UI tests for screens; backend API tests; and a manual pass on the restaurant's tablet. |

Money is always integer cents. Menu items keep the paper menu's number (`code`, e.g. "L6", "19").

## Requirements

**Must have (v1): orders become printed tickets**

- Owner account, staff with roles and PINs, PIN sign-in (backlog A1–A4)
- Menu with the restaurant's real items, sizes and prices, editable in the app; sold-out switch
- Build an order: quantities, sizes, modifiers with prices (C1), item and order notes
- Order type (walk-in, pick up, phone) with customer name or phone
- Subtotal, tax and total shown before Send (B1)
- Send prints a kitchen ticket: large order number and type, time, items, modifiers, notes (H1)
- Reprint a ticket; edit or void an order after Send with a CHANGED / VOID ticket (E4, E5)
- Order history with search (E1, E2)
- An order is always saved even if printing fails; the cashier sees it and can reprint

**Should have (v2): kitchen screen**

- Open-orders board with a lane per order type (D1–D3)
- Kitchen screen showing open orders oldest first with wait time; tap to mark Ready
- Rush alert when an order waits longer than a set number of minutes
- Customer list by phone (F2); menus by time of day (B8)

**v3: payments and reports (premium)**

- Card payments, payment methods, cash drawer, tips (J1–J4)
- Reports: daily summary, item sales, employee, exceptions (G1–G5)
- Clock in/out (A6)

**Later**

- Online ordering, delivery-app orders, pickup screen (I1–I3)
- Separate kitchen stations with their own tickets
- Loyalty points, caller ID, gift cards (F3, F4, J5)
- Dine-in: tables, holds, to-go lines (B9, B10, H3)

## Roadmap

1. **v1, printed kitchen tickets.** Gate: a full busy week in the family restaurant with no lost or wrong tickets.
2. **v2, kitchen screen and Ready status.** Gate: chefs use the screen every shift.
3. **v3, payments and reports.** Gate: a week of card payments reconciles with the bank.
4. **Later:** online and delivery orders, pickup screen, stations, other restaurants.

Each version is tested in the family restaurant before the next one starts, because a kitchen
tool that fails at rush hour is worse than shouting.

## Open questions

- [ ] Printing path for the Android app: cloud printing, direct from the tablet, or the terminal's built-in printer?
- [ ] Does the restaurant's ZBS POS printer support Epson Server Direct Print or Star CloudPRNT?
- [ ] Does the Wi-Fi signal reach the spot where the printer sits?
- [ ] Is the backend still Node + TypeScript + Fastify, or will it change with the Kotlin app?
- [ ] Which "own twist" features to add (see the end of `backlog.md`)?

## Decisions

| Date | Decision |
| --- | --- |
| 2026-10-09 | Pilot in the family restaurant first; sell to other restaurants later |
| 2026-10-09 | MVP prints paper tickets; the kitchen screen waits for v2 |
| 2026-10-09 | No payments in the MVP; card payments become a premium feature |
| 2026-10-09 | Cloud backend for many restaurants; no in-store computer and no offline mode |
| 2026-10-28 | Use Front (ZBS POS) as the reference product; backlog of user stories in `backlog.md` |
| 2026-10-28 | Build the app as a native Android app in Kotlin instead of a web app (PWA) |
