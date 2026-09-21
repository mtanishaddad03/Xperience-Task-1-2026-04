# CLAUDE.md — Implementation instructions (Phase 2)

## What this repo is

Xperience Task 1 — **Event RSVP Manager**. Phase 1 (design) is complete: **`DESIGN.md` is the source of truth.** Read it in full before writing any code. Every rule below comes from it, and section/label references (D1, KD8, INV-B7, Q8…) point into it.

Your job is Phase 2: implement the design, **stage by stage**, in the order and with the exit conditions defined in `DESIGN.md` → *Rollout and Migration Notes*.

## Working rules

1. **Do not invent product behaviour.** If `DESIGN.md` does not decide something, stop and ask the user. Do not pick an answer silently.
2. **One stage at a time.** Before starting a stage, say what you will change. Do not start the next stage until the current stage's exit conditions pass as automated tests.
3. **Commit per stage**, with a message naming the stage. `DESIGN.md` is committed on its own, never mixed with code (the task README requires reviewers to read the design before the code).
4. **Never commit secrets.** Never send real email during development or tests.
5. If implementation shows the design is wrong or incomplete, **stop and tell the user** — the fix goes into `DESIGN.md` first, then the code.

## Stack (fixed — do not substitute)

- Backend: `hero-backend/` — Spring Boot 4.0.5, Java 17, `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, PostgreSQL, Lombok. Port **8280**.
- Database: PostgreSQL on `localhost:5432`, database `hero`, schema `hero`, user `postgres` / `1234`. Schema managed by `ddl-auto: update` — **there is no migration tool** (T2): constraints are created once, so declare every unique constraint and foreign key correctly from the first version.
- Frontend: `hero-frontend/` — Vite, React **18** (not 19, despite the README), TypeScript, Tailwind 4. Port **5171**.
- Windows: start both with `.\start.ps1`; backend alone with `.\mvnw.cmd spring-boot:run`.

## Stop and ask the user before building these — they are not decided

| Item | Where it blocks |
|---|---|
| **KD13** — links generated at send time, only a SHA-256 hash stored. Proposed, **not confirmed**. Confirm before Stage 2. | Link record, drain, resend |
| **Email provider** — none chosen (T4). Until then, use a development sender that only logs messages. | Stage 2–3 release |
| **Q8** — per-host invitation limit (must exceed a normal 600-guest event; behaviour when exceeded is undefined). | Stage 2–3 release gate |
| **Q10** — automatic retry policy for failed sends. | Drain |
| **Q5** — which time zone the host means when entering a start time. The stored value must be an absolute instant. | Event creation |
| **Q6** — what a guest sees beyond their own state. Build the minimum until answered. | Guest page |
| **API shape** — endpoints and payloads are not in the design. Propose them, get approval, then add them to `DESIGN.md`. | Stage 2 |

## Do not build

- **Capacity editing** — blocked until Q2 is answered. No endpoint, not even hidden.
- **Management-link recovery (U9) entry point** — blocked until Q9 is answered.
- Anything in the non-goals N1–N11: accounts/login, plus-ones, co-hosts, messaging, meal/seating attributes, reminders to non-responders, public discovery, payments, recurring events, cross-event reporting, guests without email.

## Rules that are easy to get wrong — implement exactly

**Per-event lock (KD2).** Every reply change and every status change (close, cancel) starts by locking the event row (`SELECT … FOR UPDATE`, e.g. a `PESSIMISTIC_WRITE` repository method) inside one transaction. Invitations and the drain do **not** take it.

**"Now" (KD8, RC-1).** Read the time from PostgreSQL **after** the lock is acquired, with `clock_timestamp()`. **Do not use `now()` or `CURRENT_TIMESTAMP`** — in PostgreSQL they return the transaction's start time, which is exactly the bug RC-1 describes. Never use the Java clock for lock decisions or waitlist order.

**Reply unit order (Step 12):** lock event → read `clock_timestamp()` → lock check (Closed, Cancelled, or started → refuse) → capacity decision or release → promotion if a place was released → write reply(s) → record promotion notice → commit. Nothing is sent inside this unit.

**Transition table (Step 10).** Implement it as one explicit table/function, not scattered conditions. In particular:
- Confirmed → No **or Maybe** releases a place (D2).
- Waitlisted → No/Maybe releases nothing (A7).
- Waitlisted + "Yes" is a **no-op** — the entered-state time is **not** reset (INV-B6).
- Only promotion moves Waitlisted → Confirmed.
- No capacity set → every "Yes" is Confirmed; no waitlist ever (A3).

**Waitlist order (INV-D5):** entered-state time plus a unique tie-breaker (e.g. the reply's id).

**Counts are derived, never stored (KD5, KD12).** One definition per count, shared by the capacity decision and the host view.

**Records and writers (Step 10) — one writer per record:**
- Event, Guest → Event Management. Guest is create-only. Unique *(event, normalised email)* — lower-case and trim before comparing (D5).
- Reply → Reply Engine only. Unique per guest (INV-D2).
- Link → Outbox Drain only. Stores `SHA-256(link)`, never the link itself; unique. Links: ≥ 32 random bytes from `SecureRandom`, URL-safe encoded.
- Outbound message → created by its cause; **status written only by the drain**. States: queued → sending → sent · failed · skipped. Resend creates a **new** message.

**Outbox drain (D8, KD3).** The only scheduled task in the application (`@EnableScheduling` + `@Scheduled`). Per message: mark *sending* if still queued → re-check it is still true (skip if the event is cancelled; skip a promotion notice if the guest is no longer Confirmed) → **only for invitation and management-link messages**, generate the link and store its hash → send with a timeout → mark the result. **Notices never carry or replace a link (INV-B13).** A message stuck in *sending* past the timeout returns to *queued*.

**Access Gate (Steps 9, 11).** One entry point for every request. A management link and a guest link are never interchangeable. Every link failure — unknown, wrong kind, wrong event — returns the **same** response. The only requests without a link are create-event (U1) and, later, recovery (U9).

**Opening a link never changes state.** GET requests only display. Host verification (U8) requires an explicit confirm action, not a page load — mail scanners open links automatically.

**Cancel (D10, INV-B12).** In the same transaction as the cancel, record a cancellation notice for every guest whose invitation was sent. **Close (D11)** blocks every reply change, including declines.

**Frontend.** Two separate surfaces: Host Console and Guest Page. Load **no third-party resources** (the page URL is the credential). Treat all host-written text as text, never HTML. Delete the placeholder files listed in Stage 0.

## Build order and exit conditions

Follow `DESIGN.md` → *Rollout and Migration Notes* exactly:

- **Stage 0** — delete the `wasender` block from `application.yml`; delete `MessageComposer.tsx`, `RecipientTable.tsx`, `ResultsTable.tsx`, `FileUpload.tsx`, `bulkSendApi.ts`, `types.ts`.
- **Stage 1** — Reply Engine, no UI. Exit conditions are **automated integration tests** (see below).
- **Stages 2 and 3** — Event Management and the Outbox Drain, together. Gate: Q8 decided.
- **Stage 4** — promotion and cancellation notices.

## Tests

- Integration tests run against **real PostgreSQL** (the local instance, using a separate test schema). **Do not use H2** — it does not reproduce PostgreSQL's row locking or `clock_timestamp()` behaviour, which is what these tests exist to check.
- Concurrency tests fire requests at the same instant from multiple threads (e.g. `ExecutorService` + `CountDownLatch`), each in its own transaction.
- Every exit condition in the Rollout section is one test. A stage is done only when all of its tests pass.
