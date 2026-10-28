# Backlog: user stories

Every feature the reference app Front (FrontStation by ZBS POS, v3.9.0) has that our app lacks,
as 49 user stories in 10 epics, each with acceptance criteria and test cases. Built from 13
photos of the restaurant's Front terminal (project files `FrontReference/IMG_3650`–`3662`).
Scope and phases come from [`roadmap.md`](roadmap.md).

## How to use this file

- **Status:** update the Status column in the tracker when a story moves:
  `Not started` → `In progress` → `In review` → `Testing in restaurant` → `Done` (or `Won't do`).
- **Test cases:** tick `- [x]` when a test passes.
- **IDs:** use the story ID in branch names and commit messages, e.g. `b1-cart-tax-total`,
  `B1: show tax and total in the cart`.
- **Priority:** P0 = needed before the family restaurant goes live on v1; P1 = next, small and
  high value; P2 = matches a later roadmap phase; P3 = nice to have.
- **Roles:** owner, manager, cashier, chef.
- **Test kinds:** *unit* = Kotlin JUnit test of logic (prices, tax, rules); *UI* = Android UI
  test of a screen; *API* = backend test; *manual* = on the restaurant's tablet and printer.

**Definition of done:** every acceptance criterion met, every test case passes, tested in the
family restaurant, and nothing restaurant-specific hardcoded (values live in Settings or the
Menu screen).

**Rules every story inherits:** money is integer cents; every row carries its restaurant id; an
order is always saved even if printing fails; no offline mode; no payments before v3.

## What Front has that we don't

| Front screen | What Front does | Epic |
| --- | --- | --- |
| Sign-in | 4-digit PIN login, phone + password login, forgot password, create account, Clock In/Out, language switch, online indicator | A |
| Order screen | Open Item, Void, QTY keypad, PRICE override, Up/Down reorder, TOGO, DELAY, Breakline, discount, tax and total, EN/Chinese toggle, menu switcher ("All Day Menu"), Send & Pay | B |
| Modifiers | Optional priced groups (Comb & Lunch Choice, Something Extra); Special tab: Add/More/Less/No/Only/Change/Side + priced ingredients, bilingual | C |
| Home board | Lane per order type (Pick Up, Walk In) with open-order cards, + to start an order, search, delivery-app shortcuts | D |
| Orders | Any date, filters by type/status/server, search by order # or phone, totals for paid/open/voided/refunded | E |
| Member | Customers by phone with order count, first/recent order, points | F |
| Reports | Summary, Item Sales, Employee, Exception; Today/Yesterday/Week/Custom; print | G |
| More | Printer info, menu language, tax, tables, business model, pickup screen, screen alerts, version | H |
| Channels | UberEats, DoorDash, Grubhub, online orders, pickup screen, incoming-call menu | I |
| Money | Payment methods, Send & Pay, cash drawer, cash count, cash out, tips, gift cards | J |

## Story tracker

| ID | Story | Priority | Phase | Status |
| --- | --- | --- | --- | --- |
| A1 | Owner creates the restaurant account and signs in with phone or email and password, with password reset | P0 | v1 | In progress |
| A2 | Owner adds staff with a role (owner, manager, cashier) and a 4-digit PIN | P0 | v1 | In review |
| A3 | Cashier signs in on the shared tablet with a PIN | P0 | v1 | In progress |
| A4 | Roles limit what each person can do; risky actions ask for a manager PIN | P0 | v1 | In progress |
| A5 | Tablet locks after idle time and staff can switch user quickly | P1 | v1 | Not started |
| A6 | Staff clock in and out from the sign-in screen; owner sees hours | P2 | v3 | Not started |
| B1 | Show tax and total in the cart before Send | P0 | v1 | In progress |
| B2 | Add an Open Item with a typed name and price | P1 | v1 | Not started |
| B3 | Edit a cart line: quantity keypad, void line, move up or down | P1 | v1 | Not started |
| B4 | Manager overrides a line's price | P1 | v1 | Not started |
| B5 | Apply a discount to an order ($ or %) | P1 | v1 | Not started |
| B6 | Switch menu buttons between English and Chinese | P1 | v1 | Not started |
| B7 | Restaurant-defined order types (Pick Up, Walk In, Phone) and switching type mid-order | P1 | v1 | Not started |
| B8 | Menus by time of day (All Day, Lunch) with automatic switching | P2 | v2 | Not started |
| B9 | Hold (delay) items and add a break line between courses | P3 | Later | Not started |
| B10 | Mark lines To-Go and record a table or locator number (dine-in restaurants) | P3 | Later | Not started |
| C1 | Optional modifier groups with prices and min/max picks (Comb & Lunch Choice, Something Extra) | P0 | v1 | In progress |
| C2 | Shared modifier library: define a group once, attach it to many dishes | P1 | v1 | In progress |
| C3 | Kitchen instruction builder: Add/More/Less/No/Only/Change/Side + ingredient, with prices | P1 | v1 | In progress |
| C4 | Modifiers print in English and Chinese on the kitchen ticket | P1 | v1 | In progress |
| D1 | Home board with a lane per order type showing open order cards | P1 | v2 | Not started |
| D2 | Mark an order done from the board; empty lane shows All done | P1 | v2 | Not started |
| D3 | Start a new order from a lane's + button with that type preset | P1 | v2 | Not started |
| E1 | Order history for any date range with type, status and staff filters | P1 | v1 | Not started |
| E2 | Search orders by number, phone or customer name | P1 | v1 | In review |
| E3 | Totals strip: open, completed and voided counts and amounts | P1 | v1 | In progress |
| E4 | Edit a sent order and print a CHANGED ticket | P1 | v1 | In progress |
| E5 | Void an order with a reason, recorded with who did it | P1 | v1 | In progress |
| F1 | Customer saved by phone; name fills in on the next order | P1 | v1 | Not started |
| F2 | Customer list with order count, first and last order, add and edit | P2 | v2 | Not started |
| F3 | Loyalty points earned per order and redeemed as a discount | P3 | Later | Not started |
| F4 | Caller ID pop-up shows the customer and last order when the phone rings | P3 | Later | Not started |
| G1 | Daily summary: orders, gross, discounts, voids, net, tax | P2 | v3 | Not started |
| G2 | Item sales report: quantity and amount per dish | P2 | v3 | Not started |
| G3 | Employee report: orders and sales per staff member | P2 | v3 | Not started |
| G4 | Exception report: voids, price overrides, discounts, reprints | P2 | v3 | Not started |
| G5 | Pick a date range (Today, Yesterday, This Week, Custom) and print the report | P2 | v3 | Not started |
| H1 | Printer setup with test print and status | P0 | v1 | In progress |
| H2 | Menu language setting: second-language names on buttons and tickets | P1 | v1 | In progress |
| H3 | Business model settings: takeout only or with dine-in and tables | P2 | Later | Not started |
| H4 | About screen with app version and what's new | P2 | v1 | Not started |
| I1 | Online ordering page for customers | P3 | Later | Not started |
| I2 | Delivery app orders (UberEats, DoorDash, Grubhub) arrive on the board and print | P3 | Later | Not started |
| I3 | Pickup screen showing ready order numbers | P3 | Later | Not started |
| J1 | Send & Pay: take card payment on the tablet | P2 | v3 | Not started |
| J2 | Payment methods setup (cash, card, other) and paid-by on each order | P2 | v3 | Not started |
| J3 | Cash drawer: open, count and cash out at end of day | P2 | v3 | Not started |
| J4 | Tips and gratuity, tip pooling per employee | P3 | v3 | Not started |
| J5 | Gift cards: sell, load and redeem | P3 | Later | Not started |

### Where the Android app stands (2026-10-28)

Version 1 of `apps/android` covers part of these stories. What each still needs:

- **A1:** the owner sets up the tablet once (restaurant, tax, owner name and PIN); no password account until there is a backend.
- **A2:** built (roles, unique hashed PINs, turn off). Needs the restaurant test.
- **A3:** the PIN pad signs in on the 4th digit and orders record who sent them. Missing: lock after 5 wrong PINs.
- **A4:** roles are checked in the repositories, not just hidden screens. Missing: manager PIN prompt for risky actions.
- **B1:** subtotal, tax and total update live. Missing: item count and discount (B5).
- **C1:** required groups (sizes, protein picks) work. Missing: optional groups with min/max picks.
- **C2:** shared modifier lists attach to categories or dishes. Missing: reordering lists; the delete dialog doesn't name the dishes.
- **C3:** fixed actions NO / ADD / EXTRA / LIGHT / ON SIDE / SUB, priced for ADD, EXTRA and SUB, plus a free-text line note. Missing: restaurant-defined actions and ingredient groups.
- **C4:** names print in English, Chinese or both. Missing: a check that Chinese prints on the ZBS printer.
- **E2:** built (order number, name, phone). Needs the restaurant test.
- **E3:** today's order count and total, and counts per status filter. Missing: amounts per status.
- **E4:** the CHANGED ticket marks new (+), changed (*) and removed (−) lines, and the change is saved even if printing fails. Missing: manager approval for removals and a change history.
- **E5:** cancel prints a CANCELLED ticket and keeps the order. Missing: reason and manager approval.
- **H1:** prints straight to the printer (ESC/POS, port 9100) with a test ticket; failed prints show red and can be retried. Missing: printer list with online status, and the test on the real printer.
- **H2:** tickets print English, Chinese or both. Missing: choosing the second language (Chinese is fixed).

---

## Epic A: Staff accounts and sign-in

Front opens on a sign-in screen with a PIN pad, a phone + password tab and Clock In/Out.

### A1 Owner account (P0)

As an owner, I want to create my restaurant's account and sign in with my phone or email and a
password, so that only my staff can reach my menu and orders.

**Acceptance criteria**

- Sign-up asks for owner name, phone or email, password (8+ characters) and restaurant name, and creates one restaurant the owner belongs to.
- Sign-in uses hosted auth; passwords are never stored by our API.
- Forgot password sends a reset link or code; the old password stops working after reset.
- Every API call carries the signed-in user's restaurant id, and data from another restaurant is never returned.
- The sign-in screen shows the restaurant name and logo once a tablet has been set up.

**Test cases**

- [ ] Manual: sign up with a new phone number; restaurant and owner are created and the owner lands on the order screen.
- [ ] UI: wrong password shows "Phone or password is wrong" and does not sign in.
- [ ] Manual: reset password, then sign in with the old one (refused) and the new one (works).
- [ ] API: a token for restaurant 1 asking for restaurant 2's orders gets 403 or 404.

### A2 Staff and roles (P0)

As an owner, I want to add staff with a name, a role and a 4-digit PIN, so that each person
signs in as themselves.

**Acceptance criteria**

- A Staff screen (owner and manager only) lists staff with name, role and active/inactive.
- Roles are owner, manager and cashier; a chef role can be added for the v2 kitchen screen.
- PINs are 4 digits, unique within the restaurant, stored hashed.
- Deactivating a person stops their PIN at once but keeps their name on past orders.

**Test cases**

- [ ] UI: add cashier "Amy" with PIN 1234; she appears in the list.
- [ ] API: a second person with PIN 1234 is refused with "PIN already used".
- [ ] Manual: deactivate Amy; PIN 1234 no longer signs in and her old orders still show "Amy".
- [ ] UI: a cashier opening the Staff screen sees "Not allowed".

### A3 PIN sign-in on the shared tablet (P0)

As a cashier, I want to tap my 4-digit PIN on the tablet, so that I can start taking orders in
under 3 seconds.

**Acceptance criteria**

- After the owner signs the tablet in once, it stays linked to the restaurant and shows a PIN pad (dots for digits, backspace).
- The 4th digit signs in at once with no extra button.
- 5 wrong PINs in a row lock the pad for 30 seconds.
- Every order records the staff member who sent it.

**Test cases**

- [ ] UI: enter 1234; Amy is signed in and her name shows in the corner.
- [ ] UI: a wrong PIN shakes and clears the dots, no sign-in.
- [ ] Unit: 5 wrong PINs lock the pad for 30 s.
- [ ] Manual: send an order as Amy; order history shows Staff = Amy.
- [ ] Manual: works on the restaurant tablet in portrait and landscape.

### A4 Role permissions and manager approval (P0)

As an owner, I want cashiers blocked from Menu, Settings, Staff and Reports, and risky actions
to need a manager PIN, so that prices and voids are controlled.

**Acceptance criteria**

- Cashier: order screen, orders list, reprint. Manager: also Menu, voids, price overrides, discounts, reports. Owner: everything, including Staff and Settings.
- The API refuses what a role can't do; hiding a screen in the app is not enough.
- Void, price override and discount by a cashier open a manager PIN prompt; the approving manager is recorded.

**Test cases**

- [ ] UI: cashier signs in; Menu, Settings, Staff and Reports are not shown.
- [ ] API: cashier token editing a menu item gets 403.
- [ ] Manual: cashier voids a line, manager enters PIN; void goes through and the log shows both names.
- [ ] UI: cashier voids, cancels the prompt; nothing changes.

### A5 Auto-lock and switch user (P1)

As a cashier, I want the tablet to lock after it sits idle and to switch user with one tap, so
that nobody sends orders under my name.

**Acceptance criteria**

- Idle timeout is a restaurant setting (default 5 minutes, off allowed).
- Locking keeps an unsent cart; the next person to sign in sees it with a warning.
- A user icon on every screen locks at once.

**Test cases**

- [ ] Manual: set timeout to 1 minute and wait; PIN pad appears.
- [ ] UI: lock with 2 items in the cart, sign in again; both items are still there.

### A6 Clock in and out (P2)

As an owner, I want staff to clock in and out on the tablet, so that I can see hours worked.

**Acceptance criteria**

- Clock In/Out on the sign-in screen asks for a PIN and shows the time recorded.
- Owner sees an Attendance list per person per day with total hours, and can fix a missed punch with a note.

**Test cases**

- [ ] Unit: clock in 10:00, out 14:30 gives 4.5 h.
- [ ] UI: clock out with no clock-in asks the owner to fix it.

## Epic B: Order entry upgrades

Front's order screen has a tool column (Add, Reduce, Void, QTY, PRICE, MOD, DELAY, Breakline,
Up, Down, TOGO), Open Item, a bilingual toggle and a menu switcher.

### B1 Tax and total in the cart (P0)

As a cashier, I want to see subtotal, discount, tax, item count and total before I send, so that
I can tell the customer what they owe.

**Acceptance criteria**

- Cart footer shows subtotal, discount, tax, item count and total, updating on every change.
- Tax uses the restaurant's tax rate from Settings and matches the server's total to the cent.
- The app and the server use the same rounding rule; the server's total is the one saved.

**Test cases**

- [ ] Unit (app and server): subtotal 1000 cents at 8.875 % tax gives tax 89 and total 1089.
- [ ] Manual: add 3 items; count shows 3 items and the total matches the printed ticket.
- [ ] Manual: change the tax rate in Settings; the cart total changes on the next item.

### B2 Open Item (P1)

As a cashier, I want to add an item with any name and price, so that I can ring up something
that's not on the menu.

**Acceptance criteria**

- Open Item opens a name field, a price keypad and an optional second-language name.
- The item prints on the ticket like any other and is marked as open in reports.
- Price must be over $0.00; manager PIN required above a limit set in Settings.

**Test cases**

- [ ] UI: add "Extra sauce cup" $0.50; it appears in the cart and on the ticket.
- [ ] UI: with price $0.00, Add stays disabled.
- [ ] API: item sales lists it under Open items.

### B3 Edit a cart line (P1)

As a cashier, I want to change a line's quantity with a keypad, void it, or move it up or down,
so that the ticket reads in the order the kitchen cooks.

**Acceptance criteria**

- Tapping a line selects it; QTY opens a keypad (1 to 99); Void removes it; Up/Down move it.
- The ticket prints lines in the cart's order.
- Before Send, voiding needs no approval; after Send it follows E4.

**Test cases**

- [ ] Unit: QTY 12 makes the line price 12 × unit price.
- [ ] Manual: move line 3 to the top; the ticket prints it first.
- [ ] UI: void the only line; cart shows "No item selected" and Send is disabled.

### B4 Price override (P1)

As a manager, I want to change a line's price, so that I can honour a deal or fix an old price
on the spot.

**Acceptance criteria**

- PRICE on a selected line opens a keypad; a cashier needs a manager PIN (A4).
- The line shows the new price with the original struck through; the order records who changed it and from what.

**Test cases**

- [ ] Unit: override $9.50 to $8.00; total drops $1.50.
- [ ] UI: a cashier trying it gets the manager PIN prompt.

### B5 Order discount (P1)

As a manager, I want to take a dollar amount or a percent off an order, so that I can give
regulars a deal.

**Acceptance criteria**

- Discount accepts $ or %, applied before tax, and can't make the total negative.
- Preset discounts (e.g. 10 % family, $2 off) are set in Settings.
- Discount shows in the cart footer and on the ticket and is recorded with who applied it.

**Test cases**

- [ ] Unit: 10 % off a $20.00 subtotal gives −$2.00 and tax on $18.00.
- [ ] Unit: $30 off a $20 order is refused.

### B6 English / Chinese menu buttons (P1)

As a cashier, I want to switch the menu buttons between English and Chinese, so that whoever is
on the register can read them.

**Acceptance criteria**

- A language button (EN / 中文) flips item and category names; both lines show when both exist, as in Front.
- The choice is remembered per staff member.
- Search finds items by code, English or Chinese name.

**Test cases**

- [ ] UI: tap 中文; "24. Chicken Lo Mein" shows "24. 鸡捞面" first.
- [ ] Unit: search "捞面" returns the lo mein items.
- [ ] Manual: sign out and back in; the language choice is kept.

### B7 Order types set by the restaurant (P1)

As an owner, I want to choose which order types my restaurant uses (e.g. Pick Up, Walk In, Phone,
Dine In), so that the app matches how we work.

**Acceptance criteria**

- Settings lists order types with on/off, a name and a color; at least one must be on.
- The order screen header shows the type and a swap button to change it before Send.
- The ticket prints the type in large text.

**Test cases**

- [ ] UI: turn off Phone; it disappears from the order screen.
- [ ] Manual: start Walk In, swap to Pick Up, send; the ticket says PICK UP.
- [ ] API: turning off the last type is refused.

### B8 Menus by time of day (P2)

As an owner, I want separate menus such as All Day and Lunch with start and end times, so that
lunch specials only show at lunch.

**Acceptance criteria**

- A menu has a name, days and hours, and a list of categories; the switcher shows menus open now.
- The cashier can switch menus by hand.

**Test cases**

- [ ] Unit: at 11:30 the Lunch menu is offered; at 16:00 it is not.

### B9 Hold items and course break (P3)

As a cashier at a dine-in restaurant, I want to hold some items and fire them later, so that
courses come out in order.

**Acceptance criteria**

- DELAY marks lines as held; held lines print on a second ticket when fired.
- Breakline inserts a divider that prints as a line on the ticket.

**Test cases**

- [ ] Manual: hold 2 of 4 lines and send; the first ticket has 2 lines and Fire prints the other 2.

### B10 To-go lines and locator number (P3)

As a cashier at a dine-in restaurant, I want to mark some lines To-Go and enter a table or
locator number, so that the kitchen packs the right items and runners find the table.

**Acceptance criteria**

- TOGO toggles a tag on a line that prints as "TO GO".
- Locator takes a number and prints on the ticket.

**Test cases**

- [ ] Manual: tag 1 line TOGO with locator 12; the ticket shows both.

## Epic C: Modifiers

Front's item screen has a MOD tab with optional priced groups ("Comb & Lunch Choice
(Optional 1)": w. Fried Rice, w. Pork Lo Mein $4.00, No Rice; "Something Extra": Extra Shrimp
$1.00) and a Special tab that builds kitchen instructions from Add/More/Less/No/Only/Change/Side
plus ingredients in Meat, Cook, Veg, Sauce, Flavor and Nut groups, all bilingual.

### C1 Optional priced modifier groups (P0)

As a cashier, I want an item's modifier screen to offer optional groups with prices and pick
limits, so that combos and extras are rung up at the right price without typing notes.

**Acceptance criteria**

- A modifier group has a name, an optional second-language name, min picks, max picks, and options with a price (can be $0.00).
- Required groups (min ≥ 1) block Confirm until satisfied; optional groups (min 0) can be skipped.
- The modifier screen shows the item, its running total and quantity, with a side list to jump between groups.
- Chosen modifiers print indented under the item on the ticket and add to its price.
- Sizes (Pt/Qt) and protein picks are groups with min 1, max 1.

**Test cases**

- [ ] Unit: C9 Hunan Chicken $10.20 + "w. Pork Lo Mein" $4.00 + Extra Shrimp $1.00 = $15.20.
- [ ] Unit: in a group with max 1, picking a second option replaces the first.
- [ ] UI: a required group left empty keeps Confirm disabled with "Pick 1 from Size".
- [ ] Unit: line price = (base + sum of modifier prices) × quantity, in cents.
- [ ] Manual: every Pt/Qt item from the paper menu orders at the right price.

### C2 Shared modifier library (P1)

As an owner, I want to define a modifier group once and attach it to many dishes, so that
changing the lunch-choice price updates every lunch special at once.

**Acceptance criteria**

- The Menu screen has a Modifiers section to add, edit, reorder and delete groups.
- A group can be attached to items or to a whole category; item-level attachments can be removed.
- Deleting a group used by items asks to confirm and lists them.

**Test cases**

- [ ] Manual: attach "Comb & Lunch Choice" to the Lunch category; all lunch items show it.
- [ ] API: change w. Pork Fried Rice from $1.50 to $1.75; new orders charge $1.75, past orders keep $1.50.

### C3 Kitchen instruction builder (P1)

As a cashier, I want to tap an action (Add, More, Less, No, Only, Change, Side) and then an
ingredient, so that special requests are fast, priced and readable in the kitchen.

**Acceptance criteria**

- Actions and ingredient groups (Meat, Cook, Veg, Sauce, Flavor, Nut) are set by the restaurant; ingredients have prices for Add and More only.
- No + Onion adds "NO Onion" to the line; Add + Shrimp adds "ADD Shrimp $1.75".
- A pencil button adds free text, replacing quick-note chips.
- Instructions print in capitals under the item.

**Test cases**

- [ ] Unit: No + Onion gives "NO Onion" with no price change.
- [ ] Unit: Add + Shrimp gives "ADD Shrimp" and +$1.75.
- [ ] Manual: Less + Sauce and Side + Sauce on one line both print.

### C4 Bilingual modifiers on tickets (P1)

As a chef, I want modifiers and instructions printed in Chinese as well as English, so that I
don't misread a request.

**Acceptance criteria**

- Each modifier and ingredient can carry a Chinese name; the ticket prints the language chosen in Settings (English, Chinese or both).
- Chinese prints correctly on the kitchen printer (GB18030 or image mode).

**Test cases**

- [ ] Manual: a ticket with "NO Onion 不要洋葱" prints legibly on the restaurant's printer.
- [ ] Unit: with English only, the ticket has no Chinese.

## Epic D: Open orders board

Front's home screen shows a lane per order type (Pick Up, Walk In) with open orders as cards,
"ALL DONE" when empty, a + to start an order, search and an All Open Orders filter.

### D1 Lane per order type (P1)

As a cashier, I want a home board with a lane per order type showing every open order, so that I
can see at a glance what the kitchen is still making.

**Acceptance criteria**

- One lane per enabled order type (B7), in that type's color.
- A card shows order number, customer name, minutes since sent and item count; oldest first.
- Cards update within 3 seconds when any tablet sends or completes an order.

**Test cases**

- [ ] Manual: send a Pick Up order on tablet 1; the card appears on tablet 2 within 3 s.
- [ ] UI: the card shows "#12 · Wong · 4 min · 3 items".

### D2 Mark done (P1)

As a cashier, I want to mark an order Ready or Picked up from its card, so that the board only
shows what's still open.

**Acceptance criteria**

- Tapping a card opens the order with Ready and Picked up buttons; Picked up removes it from the board.
- An empty lane shows "All done".
- An order picked up by mistake can be reopened from order history.

**Test cases**

- [ ] UI: mark #12 picked up; the card disappears and the lane shows "All done" if it was the last.
- [ ] Manual: reopen #12 from history; the card returns.

### D3 New order from a lane (P1)

As a cashier, I want the + on a lane to start an order of that type, so that I don't have to
pick the type again.

**Acceptance criteria**

- + opens the order screen with the type set; quick buttons on the side do the same.

**Test cases**

- [ ] UI: tap + on Walk In; the order screen header reads Walk In.

## Epic E: Order history and changes

Front's Orders screen filters by date, type, status and server, searches by order number or
phone, and totals paid, open, voided and refunded orders.

### E1 Order history with filters (P1)

As a manager, I want to look up orders from any day and filter by type, status and staff, so that
I can answer "did we make that order yesterday?".

**Acceptance criteria**

- Date picker: Today, Yesterday, This Week, Custom range.
- Filters: order type, status (open, ready, picked up, voided), staff.
- Columns: order no., type, time, customer, phone, staff, total, status, actions (view, reprint).
- Sort by any column; 50 rows per page.

**Test cases**

- [ ] API: Yesterday + Pick Up returns only yesterday's pick-up orders.
- [ ] API: Staff = Amy returns only Amy's orders.
- [ ] API: a date range never returns another restaurant's orders.

### E2 Search orders (P1)

As a cashier, I want to search by order number, phone or name, so that I find a customer's order
when they walk in.

**Acceptance criteria**

- One search box; digits match order number or phone; letters match name. Results in under 1 second.

**Test cases**

- [ ] API: "9377" finds the order for 803-537-9377.
- [ ] API: "12" today finds order #12.

### E3 Totals strip (P1)

As a manager, I want the counts and amounts for open, completed and voided orders under the list,
so that I can check the day at a glance.

**Acceptance criteria**

- The strip shows each status's count and total for the current filter and changes with filters.

**Test cases**

- [ ] Unit: 3 completed ($45.00) and 1 voided ($10.00) read "3 Completed, $45.00 · 1 Voided, $10.00".

### E4 Edit a sent order (P1)

As a cashier, I want to add, remove or change items on an order already sent, so that the
kitchen gets the change without a new order number.

**Acceptance criteria**

- Edit opens the order in the order screen; Send prints a ticket headed "CHANGED #12" listing only added (+) and removed (−) lines.
- Removing a sent line needs manager approval (A4).
- The order keeps a history of each change with time and staff.

**Test cases**

- [ ] Unit: adding 1 egg roll to #12 produces a CHANGED ticket with only "+ 1 Egg Roll".
- [ ] UI: removing a line as cashier opens the manager PIN prompt.
- [ ] Manual: with the printer off, the change is saved and marked for reprint.

### E5 Void an order with a reason (P1)

As a manager, I want to void an order with a reason, so that voids are explained and show in the
exception report.

**Acceptance criteria**

- Void asks for a reason (preset list from Settings plus Other) and manager approval.
- Voided orders stay in history, greyed out, and print a VOID ticket to the kitchen.

**Test cases**

- [ ] Manual: void #12 "Customer left"; history shows Voided, the reason and manager; the kitchen gets VOID #12.
- [ ] Unit: voided orders are excluded from net sales (G1).

## Epic F: Customers

Front's Member screen keeps customers by mobile number with order count, first and recent order
and points.

### F1 Remember customers by phone (P1)

As a cashier, I want the customer's name to fill in when I type a phone number we've seen before,
so that phone orders go faster.

**Acceptance criteria**

- Sending an order with a phone number creates or updates a customer for this restaurant.
- Typing 4+ digits suggests matching customers; picking one fills name and phone.
- The order screen shows the customer's last order with a Repeat button that copies its items.

**Test cases**

- [ ] Manual: order for 803-537-9377 "Lee"; on the next order typing 9377 suggests "Lee".
- [ ] UI: Repeat last order fills the cart at today's prices.
- [ ] API: the same phone at another restaurant is not suggested.

### F2 Customer list (P2)

As an owner, I want a customer list with order count, first and last order and search, so that I
know my regulars.

**Acceptance criteria**

- Columns: phone, name, order count, first order, last order; search by name or phone; add and edit.

**Test cases**

- [ ] API: a customer with 3 orders shows count 3 and the right dates.

### F3 Loyalty points (P3)

As an owner, I want customers to earn points per dollar and redeem them, so that regulars come back.

**Acceptance criteria**

- Points per dollar and reward value are settings; redeeming applies a discount (B5).

**Test cases**

- [ ] Unit: a $20 order at 1 point per dollar adds 20 points.

### F4 Caller ID pop-up (P3)

As a cashier, I want the caller's name and last order to pop up when the phone rings, so that I
can greet them and start the order.

**Acceptance criteria**

- Needs a caller ID device or VoIP integration (to be chosen); unknown numbers start a new customer.

**Test cases**

- [ ] Manual: a known number rings and the pop-up shows name and last order within 2 s.

## Epic G: Reports

Front's Data Report has Summary, Item Sales, Employee and Exception reports with
Today/Yesterday/This Week/Custom and Print. The roadmap puts reports in v3; order-count and item
reports don't need payments, so they could start earlier.

### G1 Daily summary (P2)

As an owner, I want a summary of orders, gross sales, discounts, voids, net sales and tax, so that
I can check the day against the register.

**Acceptance criteria**

- Gross = sum of line prices; net = gross − discounts − voids; tax and grand total shown; filter by order type and staff.
- Every figure has an info tip saying how it's calculated.

**Test cases**

- [ ] Unit: a fixture day with 10 orders, 1 void and 1 discount matches a hand calculation to the cent.

### G2 Item sales (P2)

As an owner, I want quantity and sales per dish and per category, so that I know what sells.

**Acceptance criteria**

- Sorted by quantity or amount; modifiers counted separately; open items grouped.

**Test cases**

- [ ] API: 3 orders of Chicken Lo Mein Qt show 3 and 3 × price.

### G3 Employee report (P2)

As an owner, I want orders, sales and voids per staff member, so that I can see who did what.

**Acceptance criteria**

- One row per staff member for the range; hours worked added once A6 exists.

**Test cases**

- [ ] API: Amy sent 5 orders and her row shows 5.

### G4 Exception report (P2)

As an owner, I want a list of voids, price overrides, discounts and reprints with who did and
approved them, so that I can spot problems.

**Acceptance criteria**

- One row per event: time, order, type, amount, staff, approver, reason.

**Test cases**

- [ ] API: a price override from B4 appears with both names.

### G5 Date range and print (P2)

As an owner, I want to pick Today, Yesterday, This Week or a custom range and print the report on
the receipt printer, so that I can file it at closing.

**Acceptance criteria**

- The range applies to all reports; days follow the business day (orders after midnight before closing count for that day).
- Print sends an 80 mm layout to the printer.

**Test cases**

- [ ] API: custom 10/01 to 10/07 returns 7 days of totals.
- [ ] Manual: the printed report fits 80 mm with no cut-off text.

## Epic H: Settings and devices

Front's More screen has 22 setting tiles. These are the ones that fit our plan.

### H1 Printer setup with test print (P0)

As an owner, I want to add my kitchen printer, see whether it's online and print a test ticket, so
that I know tickets will come out before we open.

**Acceptance criteria**

- Decide the printing path first (roadmap open question): cloud printing (Epson Server Direct Print / Star CloudPRNT), direct from the Android tablet over Wi-Fi/LAN (ESC/POS, port 9100), or a terminal's built-in printer.
- Printer screen lists printers with name, connection, status (online, offline, out of paper when reported) and last contact.
- Test print prints a sample ticket with the restaurant name and time.
- A printer that can't be reached shows a red banner on the order screen; orders still save and can be reprinted.
- Check whether the restaurant's ZBS POS printer works with the chosen path.

**Test cases**

- [ ] Manual: test print reaches the printer within 5 s.
- [ ] Manual: unplug the printer; the banner appears; a sent order is saved and marked "not printed".
- [ ] Manual: plug it back in; the queued or reprinted ticket prints.

### H2 Menu language setting (P1)

As an owner, I want to choose a second language for the menu and tickets, so that the app works
for restaurants that aren't Chinese too.

**Acceptance criteria**

- Settings: primary language, optional second language, and what tickets print (primary, second, both).
- No language is hardcoded; Chinese is the family restaurant's choice.

**Test cases**

- [ ] UI: with the second language off, no Chinese shows on buttons or tickets.
- [ ] UI: with Spanish as second language, alt names show and print.

### H3 Business model (P2)

As an owner, I want to say whether my restaurant is takeout only or also dine-in, so that table
features only show when I need them.

**Acceptance criteria**

- Takeout only hides tables, locator and holds; dine-in shows them (table map later).

**Test cases**

- [ ] UI: with takeout only, no TOGO or Locator buttons show on the order screen.

### H4 About and version (P2)

As an owner, I want to see the app version and what changed, so that I know when an update arrived.

**Acceptance criteria**

- About shows the app version, restaurant id and a short what's-new list; a dot marks an unseen update.

**Test cases**

- [ ] Manual: after installing a new version, About shows the dot until opened.

## Epic I: Online and delivery channels (later)

Front has UberEats, DoorDash and Grubhub shortcuts, online orders, a pickup screen and screen alerts.

### I1 Online ordering page (P3)

As a customer, I want to order pickup from the restaurant's web page, so that I don't have to call.

**Acceptance criteria**

- Public menu page from the same menu data; orders land on the board as Online and print.
- Needs card payment (J1) or pay at pickup.

**Test cases**

- [ ] Manual: an online order prints and appears in the Online lane.

### I2 Delivery app orders (P3)

As a cashier, I want UberEats, DoorDash and Grubhub orders to arrive on the board and print, so
that I stop retyping them.

**Acceptance criteria**

- One integration partner (e.g. Otter, Deliverect or direct APIs) to be chosen; incoming orders alert with a sound.

**Test cases**

- [ ] Manual: a sandbox DoorDash order prints a ticket labelled DOORDASH.

### I3 Pickup screen (P3)

As a customer, I want a screen showing which order numbers are ready, so that I know when to come
to the counter.

**Acceptance criteria**

- A screen shows In progress and Ready numbers from D2, updating within 3 s.

**Test cases**

- [ ] Manual: mark #12 Ready and it moves to Ready on the screen.

## Epic J: Payments and cash (v3, premium)

Front has Send & Pay, payment methods, a cash drawer, cash count, cash out, tips and gift cards.
No payments until v3; the register handles them.

### J1 Send & Pay (P2)

As a cashier, I want to take card payment when I send the order, so that I don't need the old register.

**Acceptance criteria**

- Payment provider and card reader chosen in v3 planning; Send & Pay sends and charges; a failed charge leaves the order sent and unpaid.

**Test cases**

- [ ] Manual: a declined card leaves the order sent and Unpaid.

### J2 Payment methods (P2)

As an owner, I want to set which payment methods we take and record how each order was paid, so
that reports split cash and card.

**Acceptance criteria**

- Methods: cash, card, other (named); each order stores Paid by.

**Test cases**

- [ ] UI: pay cash and Orders shows Paid by Cash.

### J3 Cash drawer and cash out (P2)

As a manager, I want to open the drawer, count it and cash out at closing, so that cash matches sales.

**Acceptance criteria**

- Drawer opens via the receipt printer; count by bill and coin; over/short shown against expected cash.

**Test cases**

- [ ] Unit: expected $200, counted $195 shows Short $5.00.

### J4 Tips (P3)

As an owner, I want tips and gratuity recorded and split per employee, so that staff are paid correctly.

**Acceptance criteria**

- Tip settings (suggested %, gratuity for large parties); tip report per employee.

**Test cases**

- [ ] Unit: 2 card tips of $3 for Amy total $6.

### J5 Gift cards (P3)

As an owner, I want to sell and redeem gift cards, so that customers can give our food as a gift.

**Acceptance criteria**

- Card number, balance, load and redeem; balance can't go below $0.

**Test cases**

- [ ] Unit: redeeming $15 from a $10 card applies $10 and refuses the rest.

---

## Our own twist

Starter ideas where we could beat Front; not stories yet. Picked ideas become stories in a new
epic T with the same format.

- [ ] **Busy-hour mode:** one-tap ordering of the top 20 dishes from the last 4 weeks.
- [ ] **Quote a pickup time:** estimate ready time from open orders; print it on the ticket.
- [ ] **Text when ready:** marking an order Ready (D2) sends the customer an SMS.
- [ ] **Kitchen-first tickets:** biggest text for order number and type, modifiers grouped by station, Chinese first for chefs.
- [ ] **Setup in 15 minutes:** photograph a paper menu and the app drafts the menu.
- [ ] **Owner phone view:** live sales and open orders on the owner's phone, read only.
- [ ] **Simpler than Front:** one clean order screen instead of an 11-button tool column; advanced tools behind "More".

## Left out on purpose

| Front feature | Why |
| --- | --- |
| Sync Data, unsynced orders | Front stores orders on the device and syncs; we are cloud-only with no offline mode |
| Networking Setting | The tablet uses the restaurant Wi-Fi through Android settings |
| Inventory | Not asked for; low value for a takeout kitchen today |
| Electronic Scale | For priced-by-weight items; not used at the family restaurant |
| Main and Customer Display | Only matters once payments (J) exist |
| Screen Alert Management | Covered by the new-order sound in I2 |
| Incoming call menu settings | Covered by F4 |
