# DLMP — QA Test Runbook

Everything worth clicking through, organized by who's clicking. Each case has a goal, exact steps, and what "correct" looks like. Cases marked **[NEW]** exercise things added in the latest release: live push notifications, the extra-payment flow, and tightened validation.

## Environments & accounts

| | |
|---|---|
| Frontend (test here) | https://frontend-2yns9tuym-automation21.vercel.app |
| API gateway | https://dlmp-gateway.onrender.com |
| Admin login | `tangellamanoj9@gmail.com` — password in your `.deploy-secrets` (`ADMIN_PASSWORD`) |
| Loan officer account | None seeded — register a customer, then promote it (see Staff section) |

> **Before you start — cold starts are normal.** All 6 services are on Render's free tier and sleep after ~15 minutes idle. The *first* request to a sleeping service can take 60–150 seconds and may show a spinner, a timeout, or a brief 502 that resolves on retry. That's infrastructure, not a bug — only file it if it doesn't resolve within ~3 minutes.

---

## Customer (`ROLE_CUSTOMER`)

The borrower's side: register, apply, track, and repay. Register a fresh account for this pass — reusing one with existing loans will skip the empty-state checks.

### C1 — Register & first login `[core]`
1. Go to the frontend, choose *Register*.
2. Fill in name, a fresh email, a 10-digit phone starting 6–9, and a password missing one required character class (e.g. no digit).
3. Submit, confirm the inline password error is specific ("must contain a digit"), then fix it and resubmit.

**Expect:** You land on the customer dashboard, empty state ("no loans yet"), and a "Welcome to DLMP" toast/notification appears within a few seconds.

### C2 — Apply for a loan, happy path `[core]`
1. Open *Apply for a Loan*, pick `PERSONAL`, amount ₹50,000, tenure 6 months, income ₹80,000.
2. Check the live EMI/interest/total preview updates as you change amount or tenure.
3. Submit.

**Expect:** Redirects to the loan detail page, status `PENDING_REVIEW`, EMI/credit score/risk category populated from the server (not just the client-side estimate).

### C3 — Per-loan-type bounds enforced client-side `[NEW]`
1. On the apply form, switch loan type to `GOLD` (₹10,000–₹5,000,000, max 36 months).
2. Enter amount ₹8,000,000 and tenure 60 months, then try to submit.

**Expect:** Two inline errors, specific to GOLD's own bounds (not a generic "too large") — the amount error names the ₹10,000–₹5,000,000 range and the tenure error names the 36-month cap. No round-trip to the server needed to learn this.

### C4 — Track an application through its lifecycle `[core]`
1. Leave the loan detail page open in this tab.
2. As staff (see Staff section, another tab/browser), approve then disburse the same loan.
3. Watch this tab without refreshing.

**Expect:** Status flips `PENDING_REVIEW → APPROVED → ACTIVE` on its own; an EMI schedule appears once ACTIVE, with the first installment due exactly one calendar month after disbursement regardless of what day of the month disbursement happened on.

### C5 — Pay an EMI, exact amount `[core]`
1. On an ACTIVE loan, open *Make a Payment*, leave mode on **Pay this EMI** (amount pre-filled to the exact total due).
2. Submit with UPI as the mode.

**Expect:** Payment reference returned, installment flips to PAID, and the loan's outstanding principal drops by *only the principal component* of that installment — not the full payment amount (check the EMI schedule's own numbers: principal + interest per row should equal the reduction).

### C6 — EMI mode caps at the exact amount due `[NEW]`
1. In *Pay this EMI* mode, try typing an amount larger than the displayed total due.

**Expect:** Inline error naming the exact cap, and the Pay button disables — the dialog will not let you overpay through this mode (overpaying here used to silently corrupt the payment ledger; it's blocked outright now).

### C7 — Extra payment (prepayment) `[NEW]`
1. Open *Make a Payment*, switch to **Extra payment**.
2. Enter an amount larger than one EMI (e.g. 1.5× the installment amount) and submit.

**Expect:** No cap applies in this mode. The payment succeeds and — check the EMI schedule afterward — it pays the current installment off in full and rolls the remainder into the next one, oldest-due-first.

### C8 — Loan fully repaid, auto-closes `[edge]`
1. Pay off every remaining installment on a loan (use Extra payment for speed).

**Expect:** Once the final installment clears, loan status flips to `CLOSED` on its own — no manual step — and a notification announces it.

### C9 — Payments & notifications history `[core]`
1. Check the loan detail page's payment history table matches every payment you made, in order.
2. Open the notification bell and the full Notifications page — mark one read, then "mark all read".

**Expect:** Payment list and notification list agree with what you did; unread count badge updates immediately on mark-read without a page reload.

---

## Loan Officer / Admin (`ROLE_LOAN_OFFICER`, `ROLE_ADMIN`)

These two roles currently have identical permissions on every loan-workflow and reporting endpoint — test both once to confirm they still behave the same way; don't expect different capabilities between them yet.

> **Getting a Loan Officer account:** the seeded admin only creates `ROLE_ADMIN`. To test `ROLE_LOAN_OFFICER` specifically, register a normal customer account, then have someone update that user's `role` column to `ROLE_LOAN_OFFICER` directly in the users database (there's no self-service promotion endpoint by design — role changes are a deliberately manual, out-of-band action).

### S1 — Loan queue & filtering `[core]`
1. Log in as admin, open the loan queue.
2. Filter by each status tab (Pending, Approved, Active, Rejected, Closed).

**Expect:** Counts and rows match what filter is selected; a customer's own application (from the Customer section above) shows up under Pending Review.

### S2 — Approve, reject, disburse `[core]`
1. Approve one pending application with remarks; reject another with a rejection reason.
2. Disburse the approved one.

**Expect:** Rejected application shows the reason back to the customer; disbursed one generates a full EMI schedule and moves to ACTIVE immediately (no separate manual step needed).

### S3 — Customer verification gate `[edge]`
1. Try disbursing a loan for a customer whose account you've deactivated (or simulate by checking behavior if user-service is briefly unreachable).

**Expect:** Disbursement fails closed with a clear "user is not active / verification unavailable" error — money never moves without a fresh activation check succeeding.

### S4 — Portfolio reports `[core]`
1. Open the Admin Reports dashboard.
2. Cross-check total disbursed / outstanding figures against the loans you just approved and disbursed.

**Expect:** Numbers match; the dashboard refreshes on its own within ~10s of a new event (disbursement, payment) without a manual reload.

### S5 — Role boundary: customer can't reach staff endpoints `[edge]`
1. Log in as a plain customer and try to navigate directly to the admin loan queue or reports URL.

**Expect:** Blocked with a 403 / redirected — never silently shows staff data.

---

## Live notifications (SSE) — cross-cutting

The newest piece: notifications now push instantly instead of waiting for the next poll. This is the highest-value thing to verify carefully this round.

### R1 — Push arrives without a refresh `[NEW]`
1. As a customer, keep the dashboard open and idle in one tab.
2. In a second tab/browser, log in as staff and approve that customer's loan.
3. Watch the first tab — don't touch it.

**Expect:** Within a couple of seconds: the bell badge count increments, a toast appears, and the loan's own detail page (if open) updates status — none of it needing a manual refresh.

### R2 — Reconnect after sleep/lock `[edge]`
1. Leave the tab open and the laptop idle/locked for 2+ minutes (past the ~55s server-side connection lifetime).
2. Unlock, trigger a new event from staff side.

**Expect:** Still arrives — the browser reconnects the stream automatically and you shouldn't need to reload the page. If it stops working permanently after a sleep cycle, that's worth reporting.

### R3 — Multiple tabs, same user `[edge]`
1. Open the dashboard in two tabs as the same customer.
2. Trigger one event from staff side.

**Expect:** Both tabs update — a push isn't consumed by only the first tab to receive it.

---

## Keyboard & screen reader — cross-cutting

### A1 — Dialog focus trap `[NEW]`
1. Open *Make a Payment* using only the keyboard (Tab to the button, Enter).
2. Press Tab repeatedly.
3. Press Escape.

**Expect:** Focus starts inside the dialog, cycles only through the dialog's own fields/buttons (never escapes to the page behind it), and Escape closes it with focus returning to the button that opened it.

### A2 — Loading states announce themselves `[NEW]`
1. Turn on a screen reader (VoiceOver/NVDA), navigate to a page that's loading.

**Expect:** Spinner is announced as "Loading" rather than silently skipped over.

---

## Mobile / responsive — cross-cutting

### M1 — Narrow viewport pass `[core]`
1. Resize to ~375px wide (or use device emulation).
2. Repeat C2 (apply), C5 (pay), and S1 (loan queue table) at this width.

**Expect:** Wide tables (loan queue, payment history) scroll horizontally within their own box rather than breaking the page layout; forms stack cleanly; no horizontal page scroll anywhere.

---

## Error & edge states — cross-cutting

### E1 — Wrong password / lockout `[core]`
1. Enter the wrong password 5 times in a row for one account.

**Expect:** Clear, specific error each time (not a generic 500); account handling is consistent — check whether a 6th attempt behaves differently (rate-limit or lock) and that it's communicated, not silent.

### E2 — Session expiry mid-action `[edge]`
1. Log in, then clear/corrupt the access token in dev tools (Application → Local Storage), leaving the refresh token intact.
2. Trigger any API call (e.g. refresh the dashboard).

**Expect:** Silently refreshes using the refresh token and retries the original request — you shouldn't be bounced to login for a plain expired-access-token case.

### E3 — Duplicate payment (idempotency) `[core]`
1. Pay an EMI, then quickly double-click "Pay Now" before the first request returns (or replay the same request via dev tools with the same idempotency key).

**Expect:** Exactly one payment is recorded — the second attempt returns the same payment reference marked "idempotent", never a duplicate charge.

### E4 — Oversized / malformed input `[edge]`
1. Via dev tools' Network tab, resend an apply-loan request with `principalAmount: 999999999999`.

**Expect:** Clean 400 with a validation message — never a raw 500 or an application that silently accepts an absurd amount.

---

## Reporting a bug

Note the case ID (e.g. "C6"), what you expected vs. what happened, the account/role you were using, and — if it's a live-notification or backend issue — a screenshot of the browser Network tab's `notifications/stream` connection and any console errors. Cold-start slowness alone (see top of doc) isn't a bug.
