# Event RSVP Manager — Design File

**Status:** design complete, implementation not started.
**Method:** produced by working through the 18 steps of *Building a Design File
from Scratch with an AI Partner*, in order, with Claude as the AI partner.
Section headings carry their step number so the process can be checked against
the guide.

This document is written to be **disagreed with**. Decisions carry the
reasoning that produced them so a reviewer can attack the reasoning rather than
guess at it; assumptions are separated from facts so it is clear which claims
are load-bearing; and the questions that are still open are marked open rather
than quietly resolved. Nothing here should be read as settled merely because it
is written in confident prose.

### Contents

| Section | Step |
|---|---|
| [Setup and Method](#setup-and-method-step-01) | 01 |
| [Raw Feature Brief](#raw-feature-brief-step-02) | 02 |
| [Problem Statement](#problem-statement-step-03) | 03 |
| [Goals and Non-Goals](#goals-and-non-goals-step-04) | 04 |
| [Context and Constraints](#context-and-constraints-step-05) | 05 |
| [Facts, Assumptions, and Open Questions](#facts-assumptions-and-open-questions-step-06) | 06 |
| [Actors and Workflows](#actors-and-workflows-step-07) | 07 |
| [Invariants](#invariants-step-08) | 08 |
| [Architecture](#architecture-step-09) | 09 |
| [Data Ownership and State Model](#data-ownership-and-state-model-step-10) | 10 |
| [Trust Boundaries and Security Notes](#trust-boundaries-and-security-notes-step-11) | 11 |
| [Concurrency and Correctness Notes](#concurrency-and-correctness-notes-step-12) | 12 |
| [Scalability and Multi-Tenancy Notes](#scalability-and-multi-tenancy-notes-step-13) | 13 |
| [Risks and Failure Notes](#risks-and-failure-notes-step-14) | 14 |
| [Alternatives and Tradeoffs](#alternatives-and-tradeoffs-step-15) | 15 |
| [Rollout and Migration Notes](#rollout-and-migration-notes-step-16) | 16 |
| [Pre-Review Weakness Check](#pre-review-weakness-check-step-18) | 18 |

### How to read the reference labels

| Label | Meaning | Defined in |
|---|---|---|
| **D1–D4** | Decisions that were made, with reasoning | Step 06 |
| **A1–A7** | Assumptions that could be wrong | Step 06 |
| **Q1–Q3** | Questions still open | Step 06 |
| **W1–W8** | Workflows | Step 07 |
| **INV-1–INV-12** | Named invariants | Step 08 |
| **C1–C9** | Concurrency scenarios | Step 12 |
| **R1–R16** | Risks | Step 14 |

---

## Setup and Method *(Step 01)*

- **Feature:** Event RSVP Manager (as described in the task README).
- **AI partner:** Claude, working directly inside this repo's `DESIGN.md`.
- **Process:** Work through the 18 steps in order. Each step's output is appended
  to this file as its own section. Working notes / back-and-forth with the AI
  stay out of the file — only the agreed-on result is kept here.
- **Stack (fixed by the scaffold):** Java 17, Spring Boot 4, Spring Data JPA,
  PostgreSQL, React 18 + TypeScript + Vite. *(The README says React 19; the
  actual pin is 18.3 — see Step 05.)*

## Raw Feature Brief *(Step 02)*

> Captured verbatim from the task README, as the input for the rest of the design.

- A user can create an event with a title, description, date/time, location, and optional max-capacity.
- The creator becomes the **host** of that event.
- The host can invite people by email.
- Each invitee receives a unique link and can respond: **Yes / No / Maybe**.
- The host sees a live attendance dashboard with counts and a list of attendees.
- If the event has a max-capacity and it is reached, new "Yes" RSVPs go to a **waitlist**.
- A waitlisted attendee automatically moves to confirmed if a confirmed attendee changes their RSVP to No.
- The host can cancel the event or close it to further responses at any time.
- An invitee can change their RSVP at any point **before** the event starts.
- After the event start time, all RSVPs are locked.

## Problem Statement *(Step 03)*

A host running a capacity-limited event has no reliable way to know, at any given
moment, how many people are actually coming. Invitations and replies are tracked
by hand across scattered channels, which produces three concrete failures:

1. **Overbooking.** Nothing enforces the capacity limit at the moment a reply
   arrives, so more people are told "you're in" than the venue can hold.
2. **Manual waitlist management.** When the limit is reached, the overflow is
   tracked informally, and a freed spot does not reach the next person in line
   unless the host notices and reaches out personally.
3. **No clear cut-off.** There is no defined moment at which the guest list
   becomes final, so the host is reconciling changes up to (and past) the event.

The same problem is felt from the other side. An invitee cannot tell whether
their "Yes" actually secured a place or put them on a waitlist, cannot see
whether a waitlisted place has since been confirmed, and has no way to change
their mind without going back through the host. The result is that both sides
hold a different, and usually stale, picture of the same guest list.

This system makes RSVP and capacity **enforced, live state** instead of informal
correspondence: a reply is evaluated against capacity at the moment it is made,
the outcome (confirmed or waitlisted) is told back to the invitee immediately,
waitlist promotion happens automatically when a spot frees, and responses lock
themselves at event start without the host having to do anything.

## Goals and Non-Goals *(Step 04)*

### Goals

1. One authoritative RSVP per invitee (Yes / No / Maybe), current until the
   event starts.
2. Max-capacity is enforced automatically at the moment a reply is written —
   confirmed attendees never exceed it.
3. A "Yes" that arrives after capacity is reached is **waitlisted**, not
   rejected.
4. A freed confirmed place automatically promotes the next waitlisted invitee,
   with no host action.
5. The invitee is told their own outcome — confirmed or waitlisted — at the
   moment they reply, and sees it change if they are later promoted.
6. An invitee can change their own reply, without going through the host, at
   any time before the event starts.
7. The host sees live, accurate counts and attendee status.
8. RSVPs lock automatically at event start.
9. The host can end responses early — close (event still happens) or cancel
   (event does not happen).

### Non-Goals

1. **No accounts or login.** Both the host and the invitee are identified
   solely by holding an unguessable link. Nothing is built for registration,
   passwords, or sessions.
2. **No co-hosts.** Exactly one owner per event; ownership is not transferable
   or shareable.
3. **No payments or ticketing.** No money, tickets, or refunds anywhere in the
   system.
4. **No recurring events.** Every event is a single, one-off occurrence.
5. **No plus-ones.** One invitee is exactly one place. Capacity counts
   invitees, never seats — this is what keeps waitlist promotion a simple
   ordered queue rather than a fitting problem.
6. **No public discovery.** Events are reachable only through a link the host
   distributes; there is no browsing, search, or public listing.
7. **No cross-event reporting.** Each event is viewed on its own; there is no
   aggregate view across a host's events.
8. **No alternate response channels.** Replies arrive through the link only —
   not by replying to the email, and not by SMS.

## Context and Constraints *(Step 05)*

All statements below were read out of the scaffold itself, not assumed.

### Technical

- **Stack is fixed and cannot be substituted.** Spring Boot `4.0.5`, Java 17,
  Spring MVC, Spring Data JPA, Lombok, PostgreSQL driver (`hero-backend/pom.xml`).
  Frontend is Vite + React + TypeScript with Tailwind 4.
- **Schema is managed by Hibernate `ddl-auto: update`** against schema `hero`
  (`application.yml`). There is no Flyway/Liquibase. Consequence: schema changes
  must be **additive** — new columns must be nullable or defaulted, because
  there is no backfill step and no rollback path other than a manual fix
  forward.
- **No authentication or authorization infrastructure exists.** The pom has no
  `spring-boot-starter-security`. Every access check in this design has to be
  written by hand, in application code.
- **No validation starter and no mail starter.** There is no
  `spring-boot-starter-validation` and no `spring-boot-starter-mail`, so both
  input validation and email delivery are currently unimplemented capabilities,
  not configured ones.
- **The backend is effectively empty**: one `@SpringBootApplication` class and
  a config file. No entities, repositories, controllers, or services exist.
- **The frontend contains leftovers from a different application.** `src/` holds
  empty placeholder files named `MessageComposer.tsx`, `RecipientTable.tsx`,
  `ResultsTable.tsx`, `FileUpload.tsx`, `bulkSendApi.ts` and `types.ts` — a bulk
  messaging tool, not an RSVP manager. They are 0 bytes and carry no behaviour,
  but the naming will mislead a reader and should be removed rather than reused.
- **Single instance, single process.** `start.ps1` launches exactly one backend
  on port 8280 and one frontend on 5171, with no clustering. Therefore
  concurrency correctness may rely on the database, but must not assume
  in-process locking will still work if this is ever scaled out.

### Product

- Scope is fixed by the brief and by the Non-Goals above.
- **A live conflict exists between the brief and the scaffold.** The brief says
  invitees are invited **by email**, but `application.yml` wires a WhatsApp
  sending vendor (`wasender.api-url: https://wasenderapi.com/api/send-message`).
  This is carried into Step 06 as an open question, not silently resolved here.

### Operational

- Local single-developer Windows setup; Postgres expected on `localhost:5432`
  with credentials `postgres` / `1234` hardcoded in `application.yml`.
- **A live third-party API key is committed in plaintext** in `application.yml`
  as the default value of `WASENDERAPI_KEY`. It is inherited from the upstream
  template repository, which is public, so it is already exposed and is present
  in this fork's git history as well. It is recorded here as a present fact, not
  a hypothetical risk.
- No CI, no deployment pipeline, no logging or monitoring beyond Hibernate's
  `show-sql: true`. Nothing restarts either process if it dies.

### Documentation accuracy

- The README states React 19; `package.json` actually pins React `^18.3.1`.
  A design that assumes React 19-only behaviour would be built on a false
  premise, so React 18 is treated as the fact.

## Facts, Assumptions, and Open Questions *(Step 06)*

### Facts

Each of these is either stated in the brief or readable in the scaffold. None
of them is a choice this design made.

1. An event carries a title, description, date/time, location, and an
   **optional** max-capacity. The creator is its host.
2. Invitees reply through a unique link, choosing Yes, No, or Maybe.
3. A "Yes" that arrives once capacity is reached is waitlisted, not refused.
4. When a confirmed attendee changes to No, a waitlisted attendee is promoted.
5. Replies can be changed until the event starts; afterwards they are locked.
6. The host can cancel the event, or close it to further replies, at any time.
7. The scaffold has no authentication or authorization of any kind.
8. The scaffold wires a WhatsApp vendor while the brief specifies email.
9. Schema evolution is `ddl-auto: update` only — additive, no migrations.
10. The application runs as exactly one backend process.

### Decisions made (previously ambiguous, now settled)

These were genuine choices. They are recorded with their reasoning so a
reviewer can disagree with the reasoning, not just the outcome.

**D1 — Invites are delivered by email.**
The brief is the requirement; the WhatsApp wiring is residue from a different
application that happened to share this scaffold. The `wasender` block is to be
**deleted**, not merely left unused — while it remains, the exposed vendor key
stays a live liability for no benefit. *If this is wrong*, both the invitee data
model and the delivery component need rework, not a small edit.

**D2 — Only "Yes" consumes capacity. "Maybe" does not.**
"Maybe" means the invitee has not committed, and holding a place for an
uncommitted person is what causes a half-empty room. *Known cost, accepted:* if
many "Maybe" invitees switch to "Yes" close to the event, they will land on the
waitlist in a burst. This is a deliberate trade, not an oversight.

**D3 — Identity is capability-based: holding the link is the authorization.**
There is no login for either role. The host receives one durable management
link at creation; each invitee receives their own. *Known cost, accepted:* a
leaked link is a full compromise with no revocation path, and a host who loses
their link permanently loses control of that event.

**D4 — One invitee is exactly one place.**
Capacity counts invitees, not seats (see Non-Goal 5). This is what keeps the
waitlist an ordered queue rather than a fitting problem.

### Assumptions

Not established by the brief. Each is a reasonable reading that could turn out
to be wrong, and each is written so that its failure is recognisable.

1. **The waitlist is first-in, first-out**, ordered by the time the "Yes" was
   recorded. *If wrong:* promotion needs a real ranking model (host-curated or
   priority), not a timestamp read.
2. **An event with no max-capacity never waitlists anyone** — every "Yes" is
   confirmed. *If wrong:* "unlimited" needs to become a distinct, explicit state
   rather than the absence of a value.
3. **The number of people invited is unbounded** and independent of capacity —
   a host may deliberately invite more people than the room holds. *If wrong:*
   invitation itself needs a validation rule that does not exist today.
4. **"Close" and "cancel" are different.** A closed event still happens but
   accepts no further replies; a cancelled event does not happen at all. Both
   are final. *If wrong:* one of them needs to become reversible, which changes
   the event state machine.
5. **Links are durable and reusable**, not single-use — an invitee returns to
   the same link to see or change their answer. *If wrong:* the whole
   capability model needs expiry and reissue, which it has no place for.
6. **A waitlisted invitee changing to No frees nothing**, because they were not
   holding a place. Only a *confirmed* attendee leaving triggers promotion.
7. **Promotion is silent.** A promoted invitee sees their new status when they
   next open their link; the system does not push them a message. *If wrong:*
   delivery stops being invite-only and becomes part of the RSVP flow.

### Open Questions

Unresolved. Listed as a first-class section, not a footnote.

**Q1 — What happens when a host lowers max-capacity below the number of people
already confirmed?**
Three defensible answers exist: reject the edit; demote the most recently
confirmed attendees back to the waitlist; or allow the event to sit temporarily
over capacity and let attrition resolve it. Each one breaks a different
promise — respectively the host's control, an attendee's confirmed place, or
the capacity invariant itself.
**Status: genuinely open.** *Not blocking:* everything else can be built first.
The capacity-reduction endpoint must not ship until this is answered, because
its effect on already-confirmed attendees is currently undefined.

**Q2 — Is a promoted invitee actively told, or do they find out by returning?**
Assumption 7 currently says silent, which is the cheapest option and the one
that needs no delivery infrastructure beyond invitations. But a person who was
told "you are on the waitlist" and is never told otherwise will simply not
attend, which defeats the promotion mechanism entirely.
**Status: open**, and worth resolving before implementation, because it decides
whether delivery is a one-time concern or an ongoing one.

**Q3 — If a host loses their management link, is there any recovery path?**
D3 implies no. That is tolerable for a course exercise with one technical host,
and clearly unacceptable for real users.
**Status: open**, deliberately deferred — answering it properly means
introducing accounts, which Non-Goal 1 excludes.

## Actors and Workflows *(Step 07)*

### Actors

| Actor | What it is | How it is identified | What it may do |
|---|---|---|---|
| **Host** | The person who created the event | Holds the event's management link (D3) | Create, invite, view the dashboard, close, cancel |
| **Invitee** | A person invited to one event | Holds their own personal link (D3) | Submit and change their own reply only |
| **System** | The application itself | — | Enforce capacity, promote from the waitlist, lock at start |

The System is listed as an actor deliberately: promotion and locking are things
the application does **on its own**, without either human asking, and a design
that leaves them implicit will produce code where nobody owns them.

### W1 — Create Event

- **Actor:** Host. **Precondition:** none — no account is required.
- **Flow:** Host submits title, description, start date/time, location, and
  optionally a max-capacity → the event is created → the host is handed the
  single management link that is their only way back in.
- **System checks:** start time must be in the future (INV-8); max-capacity, if
  given, must be a positive number.
- **Result:** the event exists and accepts replies. Capacity is *not* checked
  here — there are no replies yet to check it against.
- **Failure mode:** the host does not save the link. There is no recovery (Q3).

### W2 — Invite People

- **Actor:** Host. **Precondition:** the event exists and is not cancelled.
- **Flow:** Host supplies one or more email addresses → one invitee record is
  created per address, each with its own unguessable token → each is handed to
  delivery to be sent by email (D1).
- **System checks:** the caller must hold this event's management link. No
  limit is applied to how many people may be invited (Assumption 3).
- **Result:** each invitee exists in a Pending state, having not yet replied.
- **Failure modes:** delivery silently fails and the invitee never learns they
  were invited, with the host unaware; a retried request creates a duplicate
  invitee instead of resending to the existing one.

### W3 — Submit First Reply

- **Actor:** Invitee, through their own link.
- **Precondition:** the event is not closed, not cancelled, and has not started.
- **Flow:** Invitee chooses Yes / No / Maybe → the lock check runs → if the
  answer is "Yes", the capacity gate runs and decides **Confirmed** or
  **Waitlisted** → the outcome is written and shown back to the invitee
  immediately (Goal 5).
- **System checks:** lock check (INV-4); capacity gate on "Yes" only (D2); the
  token must resolve to exactly one invitee of exactly one event (INV-5).
- **Failure modes:** two invitees claim the last remaining place at the same
  instant; an unknown or foreign token must be refused without revealing
  whether the event exists.

### W4 — Change Reply

- **Actor:** Invitee, through the same link. **Precondition:** as W3.
- **Flow:** Invitee submits a different answer → the lock check re-runs → the
  transition either **releases** a confirmed place (triggering W5) or
  **requests** one (re-running the capacity gate).
- **Transitions that release a place:** Confirmed → No, and Confirmed → Maybe.
- **Transitions that release nothing:** anything starting from Waitlisted,
  because a waitlisted invitee was never holding a place (Assumption 6).
- **Failure modes:** the same invitee submitting from two tabs at once; a
  release and a new "Yes" racing for the same freed place.

### W5 — Waitlist Promotion *(System)*

- **Actor:** System. **Trigger:** a confirmed place is released by W4.
- **Flow:** if the waitlist is non-empty, the **earliest** waitlisted invitee
  (Assumption 1) becomes Confirmed, in the same transaction as the release that
  freed the place.
- **System checks:** exactly one invitee is promoted per released place — never
  zero, never two (INV-3).
- **Result:** promotion is silent under Assumption 7; the promoted invitee
  discovers it on their next visit. **This is exactly what Q2 questions.**
- **Failure modes:** two concurrent releases both select the same waitlisted
  person; a promotion picks someone belonging to a different event.

### W6 — View Dashboard

- **Actor:** Host, through the management link.
- **Flow:** live counts (confirmed / waitlisted / declined / maybe / pending)
  and the attendee list are computed from current records on every read.
- **System checks:** the management link must belong to **this** event. This
  read is as privileged as any write — it exposes the full guest list with
  email addresses — and is checked identically.

### W7 — Close or Cancel

- **Actor:** Host. **Precondition:** the event is not already closed/cancelled.
- **Flow:** **Close** stops further replies while the event still happens;
  **Cancel** means the event does not happen. Either way, every subsequent RSVP
  write is refused by the lock check.
- **Result:** final and irreversible (Assumption 4); affects every invitee at
  once.
- **Failure mode:** a reply commits at the same moment the host cancels,
  consuming a place on an event that no longer exists.

### W8 — Lock at Start *(System)*

- **Actor:** System. **Trigger:** the event's start time passing.
- **Flow:** no job runs. "Locked" is **derived** at the moment of each request
  by comparing the event's start time against the database's clock, inside the
  same transaction as the write it guards.
- **Why derived rather than scheduled:** a scheduled job would need a second
  always-running process, and would still not be safe on its own — a write that
  begins before the boundary and commits after it must be refused regardless.
  Since the check has to exist inside the transaction anyway, the job adds a
  moving part and buys nothing.

## Invariants *(Step 08)*

Named, so that later sections and any future code review can refer to them
directly. Each states what must **always** hold, and where it is enforced.

### Business invariants

- **INV-1 — Capacity is never exceeded.** For an event with a max-capacity, the
  number of Confirmed invitees never exceeds it, at any instant, including
  during concurrent writes. *Enforced by:* the capacity gate, atomically with
  the write that would breach it.
- **INV-2 — Exactly one current reply per invitee.** An invitee has one status,
  not a history and not two. *Enforced by:* a single mutable field; submitting
  is an update keyed by the invitee's token, never an insert.
- **INV-3 — Every released place promotes exactly one person.** If a confirmed
  place is released while the waitlist is non-empty, exactly one waitlisted
  invitee — the earliest — becomes Confirmed. Never zero, never two.
  *Enforced by:* running promotion in the same transaction as the release.
- **INV-4 — Nothing is writable after lock.** Once the event has started, or
  has been closed or cancelled, no RSVP may be created or changed. *Enforced
  by:* the lock check, evaluated against the database clock inside the writing
  transaction.
- **INV-5 — A link acts only within its own event.** A token grants access to
  the one invitee (or the one event) it was issued for. Matching a token
  somewhere in the system is not sufficient; it must match *within* the event
  being acted on. *Enforced by:* scoping every query by event.
- **INV-8 — An event never starts in the past.** At creation, the start time is
  in the future — otherwise the event is born locked and can never collect a
  single reply.
- **INV-9 — No capacity means no waitlist.** If max-capacity is unset, every
  "Yes" is Confirmed and the waitlist is necessarily empty (Assumption 2).

### Data integrity invariants

- **INV-6 — Tokens are unique and unguessable.** Every token is globally unique
  (database constraint) and generated from a cryptographic random source —
  never a sequential identifier, which would make every other invitee's link
  trivially discoverable.
- **INV-7 — Every invitee belongs to a real event.** Enforced by a foreign key,
  not by application code.
- **INV-10 — Counts are derived, never stored.** The confirmed count is always
  a count of actual Confirmed records. There is no cached counter column that
  could drift away from reality.

### Authorization invariants

- **INV-11 — Only the holder of the management link may act as host** — on
  every host operation, including the dashboard read.
- **INV-12 — An invitee may only affect their own reply.** The one deliberate
  exception is promotion (W5), which writes to a *different* invitee — and that
  invitee is always chosen by the system, never named by the request.

### Tension between invariants

INV-1 and INV-3 pull in opposite directions the moment Q1 is answered: if a
host lowers capacity below the confirmed count, either INV-1 breaks (the event
sits over capacity) or an already-confirmed attendee is demoted. There is no
answer that preserves both. This is precisely why Q1 is recorded as open rather
than guessed at.

## Architecture *(Step 09)*

The backend is split by **responsibility**, not by technical layer. The reason
is specific rather than stylistic: the capacity rule is the one piece of logic
in this system that is genuinely hard to get right, and a layered split would
spread it across a controller, a service, and a repository, where it becomes
three places that can each independently be wrong.

### Components

**EventService** — owns the event's lifecycle and its guest roster.
Creates events, adds invitees, serves the host dashboard, closes and cancels.
Every entry point verifies the management link against *this* event (INV-11).
It never writes an invitee's reply status.

**RsvpEngine** — owns replies, and nothing else owns them.
This is the only component that reads capacity, compares it, writes a status,
or promotes from the waitlist. Concretely it contains:
- the **lock check** — refuses any write to a started, closed, or cancelled
  event, evaluated against the database clock inside the writing transaction;
- the **capacity gate** — decides Confirmed or Waitlisted for a "Yes";
- **promotion** — runs when a confirmed place is released.

Keeping all three together is deliberate: they must run inside one transaction
holding one lock, and splitting them across components would make that
impossible to guarantee.

**InviteDelivery** — sends an invitee their link, behind an interface.
Email is the implementation (D1). The interface exists so that a future channel
change does not leak into EventService or RsvpEngine — not because another
channel is planned.

**Persistence** — one PostgreSQL schema (`hero`). It carries the integrity
invariants directly: the foreign key for INV-7, the unique constraint for
INV-6, and the row lock that INV-1 and INV-3 depend on.

### Frontend surfaces

Two separate surfaces, not one application with a role switch:

- **Host Console** — create, invite, dashboard, close/cancel. Talks to
  EventService.
- **Invitee Response Page** — shows the event and the invitee's own current
  status, and submits changes. Talks to RsvpEngine.

The existing placeholder files (`MessageComposer.tsx`, `RecipientTable.tsx`,
`bulkSendApi.ts`, …) belong to neither and are deleted rather than renamed.

### Why this shape

Host and invitee separation becomes **structural** rather than a role check
inside shared code: an invitee request never reaches EventService, and a host
request never reaches the capacity gate. The two paths cannot accidentally be
given each other's permissions, because they do not share an entry point.

Since the application runs as a single process against a single database
(Step 05), correctness under concurrency is entirely RsvpEngine's
responsibility, within one transaction — but it is delegated to the database's
locking rather than to anything in-process, so it does not silently become
wrong if this is ever run as more than one instance.

## Data Ownership and State Model *(Step 10)*

### Event state machine

**Stored status** — three values, two of them terminal:

```
                            ┌─── close ───►  CLOSED    (terminal)
                            │
     (created) ──►  OPEN ───┤
                            │
                            └─── cancel ──► CANCELLED  (terminal)
```

**Locked-ness is not on this diagram, because it is not a state.** It is a
predicate evaluated on every request:

```
     locked  ⇔  status ≠ OPEN   OR   now() ≥ startsAt
                                     ▲
                                     └─ the database's clock, read inside
                                        the same transaction as the write
```

Drawing LOCKED as a box would imply something transitions into it, which is
exactly the design this rejects (W8).

- **OPEN** — accepts replies. The only state in which RsvpEngine may write.
- **CLOSED** — the event still happens; no further replies. Final.
- **CANCELLED** — the event does not happen. Final.
- **LOCKED** is **not a stored state.** It is computed on every request as:
  `status ≠ OPEN` **OR** `now() ≥ startsAt`, using the database's clock.
  Nothing transitions "into" locked, so nothing has to fire at the right moment
  and nothing can fail to fire.

Both CLOSED and CANCELLED are terminal (Assumption 4). They are kept distinct
because they mean different things to an invitee looking at their page.

### RSVP state machine

An invitee starts at PENDING. Their first reply routes as follows — note that
"Yes" has **two** possible destinations, decided by the capacity gate:

```
                        ┌───────────────────────────┐
                        │          PENDING          │
                        │   (invited, no reply yet) │
                        └──┬─────────┬───────────┬──┘
                           │         │           │
                        Yes│       No│      Maybe│
                           │         │           │
              ┌────────────┴───┐     │           │
    capacity  │                │ full│           │
     available▼                ▼     ▼           ▼
        ┌───────────┐  ┌────────────┐ ┌──────────┐ ┌─────────┐
        │ CONFIRMED │  │ WAITLISTED │ │ DECLINED │ │  MAYBE  │
        └───────────┘  └────────────┘ └──────────┘ └─────────┘
```

Afterwards, every transition is governed by the table below. It is the
authoritative version — the diagram above covers only the first reply.

| From | Trigger | To | Releases a place? | Notes |
|---|---|---|---|---|
| PENDING | Yes, capacity available | CONFIRMED | — | |
| PENDING | Yes, capacity full | WAITLISTED | — | |
| PENDING | No | DECLINED | — | |
| PENDING | Maybe | MAYBE | — | |
| CONFIRMED | No | DECLINED | **yes** → triggers W5 | |
| CONFIRMED | Maybe | MAYBE | **yes** → triggers W5 | Follows from D2 |
| CONFIRMED | Yes | CONFIRMED | no | No-op |
| WAITLISTED | No | DECLINED | no | Was holding nothing (A6) |
| WAITLISTED | Maybe | MAYBE | no | Was holding nothing (A6) |
| WAITLISTED | Yes | WAITLISTED | no | **No-op — must not re-queue** |
| WAITLISTED | *promotion* | CONFIRMED | — | **System only** (W5, INV-12) |
| DECLINED / MAYBE | Yes, capacity available | CONFIRMED | — | Back through the gate |
| DECLINED / MAYBE | Yes, capacity full | WAITLISTED | — | No place is reserved |
| DECLINED ↔ MAYBE | No / Maybe | the other | no | |
| *any* | *anything*, event locked | unchanged | — | Refused (INV-4) |

Rules worth stating separately, because a table makes them easy to skim past:

- **WAITLISTED → CONFIRMED is reachable only by the system** (W5). An invitee
  cannot promote themselves by submitting "Yes" again; a repeated "Yes" from a
  waitlisted invitee is a **no-op** and must not re-queue them — neither to the
  front nor to the back of the line.
- **Leaving CONFIRMED releases a place** (to DECLINED or MAYBE), and that
  release triggers promotion in the same transaction.
- **Leaving WAITLISTED releases nothing** (Assumption 6).
- **Returning with "Yes"** from DECLINED or MAYBE goes back through the
  capacity gate like any other "Yes" — the earlier confirmation is not
  remembered or reserved.
- Once the event is locked, **every** transition is refused (INV-4).

### Ownership

| Data | Source of truth | Written by | Read by | Risk if this is violated |
|---|---|---|---|---|
| Event | Event row | EventService **only** | EventService, RsvpEngine | A status change from the wrong place bypasses the lock check |
| Invitee identity (email, token) | Invitee row | EventService **only** | all | — |
| Invitee **status** | Invitee row | RsvpEngine **only** | all | Split ownership of one row: if EventService ever writes status, the capacity gate is bypassed with no error anywhere |
| Confirmed count | Derived (`COUNT` of CONFIRMED) | nobody | RsvpEngine, dashboard | A cached counter would drift and silently break INV-1 |
| Waitlist order | Derived (reply timestamp) | nobody | RsvpEngine | Ties are undefined; two promotions could pick the same person |
| Dashboard | Derived | nobody | Host Console | Stale only if caching is ever added — none exists |
| "Locked" | Derived (`status`, `now()`) | nobody | RsvpEngine | Storing it would require something to fire at the right moment |

**The one structural weakness here** is that the Invitee row has two writers:
EventService owns its identity fields, RsvpEngine owns its status. The database
cannot enforce that split — only code discipline can. It is called out here,
and again in Step 14, because it is the kind of boundary that erodes quietly:
a future "resend invitation" feature is exactly the change that would casually
write `status` from the wrong side.

## Trust Boundaries and Security Notes *(Step 11)*

### Where untrusted input enters

| Boundary | Credential | What holding it grants |
|---|---|---|
| Host Console → EventService | management token in the URL | Full control of one event, including the whole guest list |
| Invitee Page → RsvpEngine | invitee token in the URL | The ability to read and change one person's reply |
| InviteDelivery → email vendor | — | The vendor **handles a working credential in transit**, before the invitee ever sees it |

There is no authenticated session anywhere. **Holding the link is the entire
authorization model** (D3), which makes every one of these boundaries a bearer
-token boundary with no second factor and no revocation.

### The cost of putting tokens in URLs

This is the direct consequence of D3 and deserves to be stated plainly rather
than discovered later:

- URLs leak through `Referer` headers to any third-party resource a page loads;
- they are stored in browser history, and on shared machines that is enough;
- they are commonly written into web-server and proxy access logs in plaintext;
- and an invitee who forwards their invitation email has handed someone else
  their identity — there is no way to tell the difference.

*Mitigations available within the current scope:* load no third-party resources
on the invitee page (so there is nothing to leak a `Referer` to), keep tokens
out of application logs deliberately, and store tokens **hashed at rest** so a
database read does not yield working credentials. What is **not** available
without contradicting Non-Goal 1 is expiry, revocation, or reissue.

### What must be checked, and where

- **Every** EventService entry point verifies the management token against the
  specific event being acted on (INV-11) — including the dashboard read.
- **The dashboard read is as privileged as any write.** It returns every
  invitee's email address. It is easy to treat a `GET` as harmless; here it is
  the single largest disclosure in the system.
- **Every** RsvpEngine entry point resolves the invitee token to exactly one
  invitee **within** exactly one event (INV-5). Confirming that a token exists
  somewhere in the table is not the same check and is not sufficient.
- **A rejected token reveals nothing.** An unknown, expired-looking, or
  foreign token gets one generic refusal. Distinguishing "no such event" from
  "wrong event" hands an attacker an oracle for enumerating events.

### Per-event isolation

Each event is effectively its own tenant. Two places can break that:

1. **The split-ownership Invitee row** (Step 10) is written by two components.
   The event-scope check has to be enforced identically in both, or a bug in
   one exposes one event's invitees through another event's dashboard.
2. **Promotion writes to a different invitee than the requester** (W5, and the
   deliberate exception in INV-12). That target must be selected by the system,
   scoped to the same event — **never** taken from a request parameter, or an
   invitee could nominate whoever they like for promotion.

### Sensitive data and irreversible operations

- **Invitee email addresses are personal data** with no separate access tier.
  The full list is exposed by a single dashboard read.
- **Tokens are credentials.** Store a **deterministic** hash (SHA-256, no
  per-row salt), not the token itself. A leaked link cannot be revoked, so
  reducing where working copies exist is the only control available.
  *The salt matters here:* a token must be **looked up** by its value, so the
  password-style approach — a unique salt per row — would make lookup
  impossible without scanning and re-hashing every row. Tokens can afford an
  unsalted hash precisely because, unlike passwords, they are long and random
  rather than guessable, so there is no dictionary to precompute.
- **Close and cancel are irreversible and affect every invitee simultaneously.**
  They warrant explicit confirmation in the interface, not a bare button.
- **The capacity-reduction endpoint must not ship while Q1 is open.** Its
  effect on already-confirmed attendees is undefined, and "undefined" here
  means quietly revoking a place someone was already promised.
- **The committed vendor key** (Step 05) is a present exposure, inherited from
  the upstream template and unfixable from this fork alone. See Step 16.

## Concurrency and Correctness Notes *(Step 12)*

Everything below is enforced with a **pessimistic row lock on the event row**
(`SELECT … FOR UPDATE`), taken at the start of any transaction that writes an
RSVP. Alternatives were considered in Step 15.

Why the lock is on the *event* row specifically: capacity is a property of the
event, and the rule being enforced is `COUNT(confirmed) ≤ capacity`. No unique
constraint can express a counting rule, and locking individual invitee rows
does not prevent a *different* invitee row from being inserted or changed
concurrently. The event row is the smallest thing that covers the whole rule.

The lock is per-event, so two different events never contend with each other.

---

**C1 — Two "Yes" replies race for the last place.** *(the central case)*
Both transactions count confirmed attendees, both see one place free, both
write Confirmed. Capacity is breached and INV-1 is broken — silently, with no
error anywhere, discovered only when too many people arrive.
*Control:* the event row lock serialises count-then-write, so the second
transaction counts **after** the first has committed and is correctly
waitlisted.

**C2 — Two released places promote the same person.**
Two confirmed attendees decline at the same moment. Both transactions look for
"the earliest waitlisted invitee" and both find the same one. Either that
person is promoted twice and the second place is silently lost, or the
promotion write collides.
*Control:* the same event row lock. Because every RSVP write already holds it
for the whole transaction, no second promotion can be selecting concurrently —
so promotion needs no separate lock of its own.

**C3 — The lock boundary is crossed mid-transaction.** *(time-of-check /
time-of-use)*
A reply is checked while the event is still open, but commits after it has
started or after the host cancelled it. INV-4 is violated by a write that
passed a check that was true when it ran.
*Control:* evaluate lock state from the **database's** `now()` and the event
row read **inside** the writing transaction — never from the application
server's clock, and never from a value read before the transaction began.

**C4 — Cancel races a reply.**
The host cancels while an invitee's "Yes" is in flight. Without serialisation
the reply can commit against a cancelled event, consuming a place on an event
that is not happening.
*Control:* close/cancel takes the same event row lock as RSVP writes, so the
two serialise against each other rather than interleaving.

**C5 — A retried submission creates a duplicate.**
The network drops, the client retries, and a naive implementation inserts a
second reply for the same person — breaking INV-2, and double-counting them
against capacity.
*Control:* submitting is an **update keyed by the invitee's token**, never an
insert. The operation is idempotent: sending the same answer twice produces the
same state as sending it once.

**C6 — One invitee, two browser tabs.**
An invitee opens their link twice and submits different answers. Last write
wins, which may not be what they actually intended last.
*Control:* an optimistic version column on the invitee row, so a write based on
a stale read is refused rather than silently applied. This is the one place
optimistic control is used, because the conflict is with **yourself** and the
right answer is to ask, not to serialise.

**C7 — A status change commits but its promotion does not.**
A confirmed attendee's decline is written, the promotion that should follow
fails, and the event is left permanently one person under capacity with a
non-empty waitlist — a state no invariant can detect after the fact.
*Control:* the release and the promotion it triggers are **one transaction**.
Both commit or neither does.

**C8 — A retried invitation creates a duplicate invitee.**
Delivery fails, the operation is retried, and a second invitee record is
created for the same person — who then holds two links and two places.
*Control:* retries act on the existing invitee by identity, via a delivery
status field. "Resend" and "invite" are different operations.

**C9 — If delivery ever becomes asynchronous.**
Not a problem today, since everything is synchronous. Recorded because it is
the specific thing that would break first: a queued "place released" event
processed after a "new Yes" it was meant to precede corrupts the capacity
count.
*Control, if that day comes:* partition strictly by event id, so all work for
one event stays ordered.

---

**Deliberately accepted:** the dashboard read takes no lock. It can therefore
show a count that is a moment stale. This is correct — the alternative is
blocking replies while a host refreshes their page, and a dashboard that is
one second behind causes no harm, because no decision is made from it that the
capacity gate does not re-check at write time.

## Scalability and Multi-Tenancy Notes *(Step 13)*

### What actually grows

Four separate axes, which fail in different ways and at different times:

1. **Number of events** in the system — the gentlest axis. Events do not
   interact, and the lock is per-event.
2. **Invitees per event** — affects dashboard queries and, more sharply,
   invitation dispatch.
3. **Burstiness of replies within one event** — the sharpest axis. A reminder
   goes out and everyone answers within the same few minutes.
4. **Events reaching their start time at once** — cheap here, precisely
   because locking is derived rather than scheduled (W8). Nothing has to fire.

### Where it breaks first

- **The per-event row lock serialises every "Yes" for that event.** This is the
  deliberate price of C1. Other events are unaffected — it is a row lock, not a
  table lock — but a single popular event's own burst queues behind itself.
- **Invitation dispatch is inline.** A host inviting 200 people pays for 200
  outbound vendor calls inside one request. Latency scales linearly with batch
  size, and a slow vendor makes it worse.
- **The dashboard is recomputed on every read.** Fine for one host refreshing;
  multiplied by polling across many live events, it becomes real query load.

### Why this is sufficient now

Guest lists are human-scale. A single Postgres instance, inline dispatch, a
whole-event row lock, and live-computed aggregates are adequate for the stated
scope, and every one of them is simpler to verify as correct than its scaled
alternative. Nothing in the brief indicates otherwise.

### What would trigger a redesign

| Trigger | Change |
|---|---|
| Lock contention becomes visible on one event | Narrow the lock from the event row to a dedicated capacity counter |
| Invitation batches grow | Move dispatch to an asynchronous worker or outbox (see Step 15, Alternative C) |
| Dashboard polling becomes measurable load | Cache with invalidation, or push updates instead of polling |
| More than one instance is ever run | Nothing — the locking is already in the database, which is why it was put there |

### Noisy neighbours

Everything shares one database, one connection pool, and one process, with no
per-event or per-host resource limits. Because invitations are unbounded
(Assumption 3), **one host can degrade every other event on the instance** —
by inviting an enormous number of people, or by running one very bursty event.
At the current single-host scope this is latent rather than active, but it
becomes real the first time this serves several hosts at once, and it is worth
knowing that the mitigation is a cap that does not exist today.

## Risks and Failure Notes *(Step 14)*

### Correctness risks

**R1 — The split-ownership Invitee row erodes silently.**
*Failure shape:* the capacity gate is bypassed with no error anywhere.
*Cause:* Step 10 gives one row two writers, and the database cannot enforce the
split. A future "resend invitation" feature writes `status` from EventService.
*Note:* worth enforcing at the repository layer rather than by convention,
because nothing will fail loudly if it is broken.

**R2 — Waitlist order is inferred, not stored.**
*Failure shape:* promotion order becomes non-deterministic, and the wrong
person is promoted.
*Cause:* FIFO is read from reply timestamps (Assumption 1). Two replies in the
same instant have no defined order.
*Note:* acceptable at current scale; the moment ordering has to change — host
priority, for instance — the query has no rank to work with.

**R3 — Q1 gets answered in code rather than in design.**
*Failure shape:* whoever implements the capacity edit picks one of the three
behaviours, and the choice — including silently demoting a confirmed
attendee — becomes permanent without ever being reviewed.
*Cause:* an open question sitting next to an obvious-looking endpoint.
*Note:* this is why Step 11 states the endpoint must not ship first.

**R4 — The derived lock depends on one discipline.**
*Failure shape:* replies are accepted after the event started.
*Cause:* one code path reads the application server's clock, or reads the event
outside the transaction. The design is correct; a single careless call is not.
*Note:* the failure is invisible in testing unless deliberately tested at the
boundary.

### Dependency risks

**R5 — A live vendor key is committed in the repository.**
*Failure shape:* an external account is usable by anyone who reads the repo.
*Cause:* `application.yml` carries the key as a default value.
*Note:* **this predates this fork.** The upstream template is public, so the
key is already exposed and sits in this fork's history too. Rotation requires
vendor-account access that this fork's owner does not have. What is actionable
here is removing it from this fork's tracked files and reporting it upstream so
whoever owns the account can rotate it. See Step 16.

**R6 — Invitation delivery has no fallback and no visibility.**
*Failure shape:* an invitee never learns they were invited, and the host never
learns they did not.
*Cause:* a single provider, dispatched inline, with no retry or status.
*Note:* combined with Q2 (silent promotion), a person can be invited *and*
promoted without ever being told either.

**R7 — One database, no backup story.**
*Failure shape:* total loss of every event and reply.
*Cause:* no backup, no recovery procedure, and nothing in the scaffold about
either.
*Note:* acceptable for a local exercise; disqualifying for anything real.

### Operational risks

**R8 — Nothing is monitored.**
*Failure shape:* R6 happens and is never noticed.
*Cause:* no logging beyond Hibernate's SQL output, no alerting.

**R9 — Nothing restarts a crashed process.**
*Failure shape:* the backend dies mid-event and replies stop being accepted;
invitees see a broken page and give up.
*Cause:* `start.ps1` launches two processes and supervises neither.

**R10 — Credentials are hardcoded for local development.**
*Failure shape:* an exposed database the moment this runs anywhere but
localhost.
*Cause:* `postgres` / `1234` in `application.yml` — a fact, not a placeholder.

### If an assumption turns out to be wrong

| # | Assumption | What breaks |
|---|---|---|
| R11 | D1: email is the channel | The invitee model and delivery both need rework, not an edit |
| R12 | D2: only "Yes" consumes capacity | The capacity gate's core rule changes, not just a filter |
| R13 | A1: the waitlist is FIFO | Promotion needs a real ranking model (R2) |
| R14 | A3: invitations are unbounded | New validation in two components that have none |
| R15 | A5: links are durable | The whole capability model needs expiry and reissue, which it has no place for |
| R16 | A7: promotion is silent | Delivery stops being invite-only and becomes part of the reply flow (Q2) |

## Alternatives and Tradeoffs *(Step 15)*

### Alternative A — Optimistic concurrency instead of a row lock

Run the capacity gate at Postgres `SERIALIZABLE` isolation and retry on
serialization failure, rather than taking `SELECT … FOR UPDATE`.

*In its favour:* correctness is equivalent — the database detects C1 and C2
rather than the application preventing them — with no lock-ordering code to get
wrong, and no risk of a lock being forgotten on some path.
*Against:* it trades a predictable queue for an unpredictable failure mode.
Under a burst on one event, serialization aborts can thrash, and every caller
needs retry logic that is itself easy to get wrong. It is also harder for a
reviewer to confirm by reading — correctness lives in an isolation level, not
in a visible line of code.
**Rejected**, because at this scale legibility is worth more than throughput.
Worth revisiting under the lock-contention trigger in Step 13.

### Alternative B — An append-only reply log instead of a mutable status

Make `RsvpEvent` the source of truth and treat the current status as a
projection, with an explicit `position` column for waitlist order.

*In its favour:* it fixes R2 outright — ordering becomes explicit and
reorderable — and gives a real audit trail when someone insists they replied
"Yes".
*Against:* a second table and a dual write on every change, to support
reporting and reordering that appear in neither the Goals nor the brief.
**Rejected as premature.** Noted because R2 is the risk that would justify
revisiting it.

### Alternative C — An outbox for delivery instead of inline dispatch

Write an outbox record in the same transaction as the invitation, and have a
background worker perform delivery.

*In its favour:* this is the strongest of the rejected alternatives. It fixes
R6 directly — delivery gets a status the host can see and a place to retry
from — and removes the batch-size latency bottleneck in Step 13.
*Against:* one more always-running process, on top of a launcher that already
supervises nothing (R9). Today that trades a visible failure for an invisible
one.
**Rejected for now, not on merit** but on operational cost. It is the first
thing to build if delivery reliability matters, and the first thing to build if
Q2 is answered "notify", because that answer makes delivery an ongoing concern
rather than a one-time one.

### Alternative D — Host accounts instead of capability links

*In its favour:* answers Q3 completely, makes revocation possible, and removes
the entire class of URL-leak problems in Step 11.
*Against:* an authentication system built from nothing — the scaffold has no
security starter — for a single-host exercise.
**Rejected** as Non-Goal 1. It is, however, the honest answer to Q3, and this
design should not pretend the capability model solves what it merely avoids.

### Alternative E — A scheduled job to lock events at start time

**Rejected** for the reason given in W8: the in-transaction check is required
for correctness regardless, so the job is a second moving part that buys
nothing. Recorded here because it is the obvious first instinct, and the reason
it is wrong is not obvious.

### The tradeoffs actually accepted

- **Pessimistic locking over optimistic retries** — correctness verifiable by
  reading the code, paid for with contention on a single busy event.
- **Derived state over stored state** (counts, waitlist order, locked-ness) —
  nothing can drift out of sync, paid for with recomputation on every read and
  an implicit ordering rule (R2).
- **One mutable status over an event log** — half the writes and no projection
  to keep correct, paid for with no audit trail and fragile ordering.
- **Inline delivery over an outbox** — no extra process to supervise, paid for
  with invisible delivery failures (R6) and latency that grows with batch size.
- **Capability links over accounts** — no auth system to build, paid for with
  leaks that cannot be revoked and a host who can lose their event permanently.
- **Single-instance assumptions** — the simplest thing that works, paid for
  with a scale-out story that is untested. The locking lives in the database
  specifically so that this particular bill stays small.

Every one of these trades operational capability for legibility and for less
code. That is the right trade for a system whose hardest requirement is a
counting rule that must never be wrong — and the wrong trade for one that has
to be reliable in production, which this is not yet.

## Rollout and Migration Notes *(Step 16)*

There is no deployed previous version, so most of what this section normally
covers — backward compatibility, phased traffic, coexistence with an old
release — does not apply. What remains is sequencing and a small number of
things that are true regardless.

### Due now, independent of whether implementation happens

**Remove the vendor key from this fork's tracked files** (R5) and report the
exposure to whoever maintains the upstream template, since only they can rotate
it. Removing it here does not erase it from git history — in this fork or in
the public upstream — so the report is the part that actually matters. The
`wasender` block goes entirely, per D1; it should not be kept "just disabled",
because a disabled path with live-shaped configuration is exactly the exposure.

**Delete the placeholder frontend files** from the unrelated application
(Step 05). They are empty, so nothing breaks, and their names actively mislead
a reviewer about what this codebase is.

### Build order, if implementation is attempted

1. **The capacity gate and promotion first, in isolation.** Before any
   controller, before any UI. Exercise them with a script that fires concurrent
   "Yes" replies at one test event and asserts INV-1 and INV-3 hold. This is
   the highest-risk code in the design and the only part whose failure stays
   silent until a real event overbooks — C1 and C2 cannot be found by clicking
   through a browser.
2. **The lock check next**, tested deliberately at the boundary (R4) —
   including a write that begins before the start time and commits after it.
3. **Then the endpoints**, then the two frontend surfaces.
4. **Not the capacity-reduction endpoint.** It stays unbuilt until Q1 is
   answered (Step 11).

### Schema evolution

`ddl-auto: update` is additive-only with no migration tool and no rollback
(Step 05). Practically:

- every new column must be nullable or have a default, because there is no
  backfill step;
- a column cannot be renamed — only added and left behind;
- the invitee's contact field can be modelled directly as an email column now
  that D1 is settled, with no channel-agnostic hedging;
- **tokens are stored hashed from the first version** (Step 11). Retrofitting
  that later is not an additive change — it invalidates every link already
  sent, and under Assumption 5 those links are the only way anyone gets back in.

### No feature flags exist

There is no flagging mechanism in the stack. Gating unfinished work therefore
means **not merging it**, not merging it behind a toggle. This applies
specifically to the Q1 endpoint.

### Operating it

Do not restart the application during a reply burst — for example immediately
after invitations go out. There is no graceful shutdown and no failover (R9),
so an in-flight capacity-gate transaction is simply dropped, and the invitee
sees a failure with no indication of whether their reply was recorded.

## Pre-Review Weakness Check *(Step 18)*

This section attacks the document above. Everything listed here is a weakness
the author found before a reviewer did, which is the point of the step — and it
is deliberately not hidden by being written last.

### Contradictions found and fixed

**"Store tokens hashed the way a password is stored" was wrong.**
Step 11 originally said tokens should be hashed like passwords. Passwords are
hashed with a unique salt per row, and a salted hash **cannot be looked up** —
you would have to read and re-hash every row to find one token. Since the
entire access model depends on looking an invitee up *by* their token, that
advice would have made the system unimplementable as designed. Corrected to a
deterministic unsalted hash, with the reasoning for why a token can afford what
a password cannot. **Recorded rather than silently edited**, because "follow
password practice" is a plausible-sounding instruction that a reviewer might
well have repeated back approvingly.

### Weaknesses still present

**W-A — "Maybe" and "No" are behaviourally identical.**
In this design, both release a confirmed place, neither consumes one, and
neither affects promotion order. The only difference is the word on the host's
dashboard. Either MAYBE is a display label rather than a state — in which case
the state machine in Step 10 is more complicated than it needs to be — or it
should do something distinct, which would reopen D2. **The design has not
decided which**, and the state machine currently implies more meaning than the
rules deliver.

**W-B — Goal 5 promises more than the design delivers.**
Goal 5 says the invitee "sees it change if they are later promoted." Assumption
7 says promotion is silent. These are only compatible under a generous reading
where "sees" means "would see, on returning to their link". A waitlisted person
with no reason to return will not see anything, so the promotion machinery can
run perfectly and still fail at its actual purpose. **Q2 is filed as an open
question, but this is arguably a design hole rather than a question** —
the strongest single objection a reviewer could raise, and the document should
not be read as having answered it.

**W-C — Two different concurrency mechanisms, with the interaction
unspecified.**
Almost everything is serialised by a pessimistic event row lock (C1–C4, C7),
but C6 introduces an optimistic version column on the invitee row. The
reasoning for each is sound in isolation; what is **not** specified is how they
interact — specifically, whether a version conflict inside a transaction
already holding the event lock rolls back the promotion that transaction may
have performed. It should, by C7, but the document does not say so, and the two
mechanisms were reasoned about separately rather than together.

**W-D — The Invitee row's split ownership is named but not solved.**
Step 10 and R1 both flag it; neither fixes it. The honest position is that this
design accepts a structural weakness because the alternative — splitting reply
status into its own table — was never seriously costed. **It should have been,
and was not.**

**W-E — Q1 is narrower than the problem it points at.**
Q1 asks what happens when a host *lowers* capacity below the confirmed count.
The same conflict arises when a host *adds* a capacity to an event that
previously had none (INV-9) and already has more confirmed attendees than the
new limit. That case is not mentioned anywhere, and it is reachable by an
ordinary host action.

**W-F — The invitee's own view is never specified.**
The document defines in detail what the *host* sees (W6) and says nothing about
what the *invitee* page shows beyond their own status. Does a waitlisted person
see their position in the queue? The total number of attendees? Other guests'
names? These are privacy decisions with real consequences, and the design is
silent on all of them — an omission, not a deliberate non-goal.

**W-G — No rate limiting anywhere.**
Tokens are unguessable (INV-6), which makes brute force impractical rather than
impossible, and nothing in the design limits how fast tokens can be tried or
how many invitations can be dispatched. Step 13 notes that unbounded
invitations are a noisy-neighbour risk; nothing mitigates it.

**W-H — No failure-response contract.**
The design states repeatedly that operations are "refused" — by the lock check,
by the capacity gate, by an invalid token — without ever defining what a
refusal looks like to a caller. Step 11 requires that refusals not distinguish
between causes, which is a *constraint* on the contract; the contract itself
does not exist. Two implementers would produce two different APIs from this
document.

**W-I — The testing strategy is one sentence.**
Step 16 says to exercise the capacity gate concurrently before building
anything else, which is the right instinct, but there is no statement of what
"correct" looks like as an assertion, and no mention of testing anywhere else.
For a system whose central requirement is a counting rule that must never be
wrong, that is thin.

### Where this design is weakest, stated plainly

If a reviewer reads only one thing here: **the concurrency treatment is the
strongest part of this document and the delivery/notification treatment is the
weakest.** C1–C9 are worked through carefully with a mechanism chosen for
stated reasons. Against that, whether anyone is ever *told* anything — invited,
waitlisted, promoted — rests on one unexamined assumption (A7), one open
question (Q2), one risk with no mitigation (R6), and an alternative that was
rejected on operational cost rather than merit (Alternative C). A system that
allocates places perfectly and never tells anyone is not solving the problem in
the Problem Statement.

### Self-assessment against the Definition of Success

| Criterion | Where | Honest assessment |
|---|---|---|
| Clear problem statement | Step 03 | Covered, both sides |
| Bounded scope, explicit non-goals | Step 04 | Eight non-goals, each with a reason |
| Assumptions visible, separated from facts | Step 06 | Facts, decisions, assumptions, and open questions are four distinct lists |
| Explicit workflows for all key actors | Step 07 | Eight, including the two the system performs unprompted |
| Named invariants | Step 08 | Twelve, with the INV-1/INV-3 tension stated |
| Real architecture boundaries | Step 09 | Host and invitee paths are structurally separate |
| Explicit state ownership | Step 10 | Two state machines and an ownership table — weakened by W-D |
| Trust / concurrency / scale treatment | Steps 11–13 | Concurrency strong; trust adequate; scale is the least developed |
| Visible risks and tradeoffs | Steps 14–15 | Sixteen risks, five alternatives, six accepted tradeoffs |
| Open questions listed | Step 06, and W-A/W-E/W-F above | Three named — plus nine weaknesses this section adds |

### What a reviewer should push on first

1. **W-B** — is silent promotion acceptable at all, or does it defeat the
   feature?
2. **W-A** — does "Maybe" earn its place as a state?
3. **W-C** — do the two concurrency mechanisms compose correctly?
4. **Q1 together with W-E** — capacity changes are the one place where two
   invariants provably cannot both hold, and the answer is still missing.
