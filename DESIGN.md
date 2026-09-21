# Event RSVP Manager — Design File

**Status:** first complete design draft. Implementation not started.
**Method:** built by working through the steps of *Building a Design File from Scratch with an AI Partner*, in order, with Claude as the AI partner. Each section heading carries the step it came from.
**Pre-review weakness check (Step 18):** performed — see the final section. What remains weak is listed there, not hidden.

This document is written to be challenged. Every decision carries the reasoning that produced it; facts, decisions, assumptions and open questions are kept in separate lists; and anything unresolved is marked as unresolved rather than quietly settled.

### Reading the labels

| Label | Meaning | Defined in |
|---|---|---|
| **F** | Confirmed fact | Facts, Assumptions, and Open Questions |
| **D** | Decision taken, with reasoning | Facts, Assumptions, and Open Questions · Alternatives |
| **A** | Working assumption — could be wrong | Facts, Assumptions, and Open Questions |
| **Q** | Open question | Facts, Assumptions, and Open Questions |
| **G / N / SC** | Goal / Non-goal / Success condition | Problem Statement · Goals and Non-Goals |
| **T / P / O / R** | Technical / Product / Operational / Organizational constraint | Context and Constraints |
| **U / S / B / X** | User-facing / Internal / Background / Failure flow | Actors and Workflows |
| **INV** | Invariant | Invariants |
| **KD** | Key design choice | Architecture |
| **E** | Trust entry point | Trust Boundaries |
| **RC / RD / RO** | Correctness / Dependency / Operational risk | Risks |

### Contents

| Section | Step |
|---|---|
| [Setup](#setup-step-1) | 1 |
| [Raw Feature Brief](#raw-feature-brief-step-2) | 2 |
| [Problem Statement](#problem-statement-step-3) | 3 |
| [Goals and Non-Goals](#goals-and-non-goals-step-4) | 4 |
| [Context and Constraints](#context-and-constraints-step-5) | 5 |
| [Facts, Assumptions, and Open Questions](#facts-assumptions-and-open-questions-step-6) | 6 |
| [Actors and Workflows](#actors-and-workflows-step-7) | 7 |
| [Invariants](#invariants-step-8) | 8 |
| [Architecture](#architecture-step-9) | 9 |
| [Data Ownership and State Model](#data-ownership-and-state-model-step-10) | 10 |
| [Trust Boundaries and Security Notes](#trust-boundaries-and-security-notes-step-11) | 11 |
| [Concurrency and Correctness Notes](#concurrency-and-correctness-notes-step-12) | 12 |
| [Scalability and Multi-Tenancy Notes](#scalability-and-multi-tenancy-notes-step-13) | 13 |
| [Risks and Failure Notes](#risks-and-failure-notes-step-14) | 14 |
| [Alternatives and Tradeoffs](#alternatives-and-tradeoffs-step-15) | 15 |
| [Rollout and Migration Notes](#rollout-and-migration-notes-step-16) | 16 |
| [Pre-Review Weakness Check](#pre-review-weakness-check-step-18) | 18 |

---

## Setup *(Step 1)*

The design was built under four standing rules:

- **Do not invent missing system details.** Every system fact is read from the scaffold or the requirement, and says where.
- **Keep facts, assumptions and open questions separate.**
- **Stay high-level.** No code; mechanisms are named by kind.
- **Build step by step**, with each section derived from the ones before it.

---

## Raw Feature Brief *(Step 2)*

### The requirement

Taken verbatim from the task README (lines 23–32), which designates it as the input for this step:

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

### Structured

| | |
|---|---|
| **What it is** | A web application in which a host invites a known list of people by personal link, and receives a live, trustworthy picture of who is coming — with an optional capacity limit, an automatically advancing waitlist, and replies that lock at the event's start. |
| **Who needs it** | **Host** — a private individual organising a wedding, party or family event, with no IT support. **Guest** — receives a link, replies, may change their mind; manages no account. |
| **Why it exists** | The brief itself states no rationale. It was supplied by the problem owner: the host **cannot know how many people will actually come**, and **spends their time chasing replies by hand.** |
| **System areas** | Event creation and lifecycle · invitation by email · identity by personal link · capturing and changing replies · capacity accounting · waitlist and promotion · host status view · time-based locking |
| **Already known** | The scaffold is Spring Boot + PostgreSQL + React; it has no authorization of any kind; it wires a WhatsApp vendor although the brief says email; it has no migration tool; it runs as one process. |
| **Unsure about** | Edge cases — cancellation, capacity changes, last-minute changes; and identity — who is actually clicking a link. |

---

## Problem Statement *(Step 3)*

### The problem

A person hosting a private event with a fixed guest list cannot know how many of the people they invited will actually attend.

Replies arrive through whatever channel each guest happens to use, at whatever time suits them, and frequently not at all. The host is the only place where those replies come together, so the guest list exists only as the host's own reconstruction of it — out of date from the moment it is made, because guests keep changing their minds.

Because the host is the sole point of consolidation:

- The list reflects the last time the host went and asked, not the present.
- A guest who changes their mind must interrupt the host to do so. Many therefore don't, and the change surfaces on the day.
- Where the venue imposes a limit, nobody but the host is checking the list against it.
- A guest who has replied has no way of learning what their reply amounted to.

### Business motivation

- **Money is committed before the count is known.** Catering is priced per head and venues by capacity, both contracted in advance. An overestimate is paid for and wasted; an underestimate is discovered in front of the guests.
- **The host's time is spent repeatedly and unproductively.** Chasing replies recurs in full every time anything changes.
- **The cost of getting it wrong is personal.** A guest turned away, or a half-empty room, is blamed on the host by people they know. A private host has no way to absorb this as an operating loss.

### Success conditions

The problem is solved when all of the following hold:

| # | Condition |
|---|---|
| **SC1** | The headcount available to the host is accurate at the moment it is consulted, and obtaining it requires no contact with any guest. |
| **SC2** | A guest changing their mind imposes no cost on the host, and none on the guest beyond making the decision. |
| **SC3** | No guest is left uncertain whether they hold a place. |
| **SC4** | **When the host has declared a capacity**, the number of guests holding places never exceeds it — at any moment, including when replies are made at the same instant. |
| **SC5** | **When a capacity exists**, a place given up by a guest does not sit unused while someone would still take it. |
| **SC6** | The guest list reaches a final state at a moment fixed in advance, without the host doing anything at that moment. |
| **SC7** | A host's decision to stop accepting replies, or to call the event off, holds for every affected guest at once. |

SC4 and SC5 are conditional because **the primary case has no capacity at all** (F15).

### Explicitly not success

- **A count that is only trustworthy if the host verifies it personally.** The verification *is* the problem.
- **An arrangement in which replying is harder than the informal channels it replaces.** A guest who ignores a message will not overcome a larger obstacle.

---

## Goals and Non-Goals *(Step 4)*

### Goals

| # | Goal | Derived from |
|---|---|---|
| **G1** | The host can obtain a headcount accurate at the moment they consult it, without contacting any guest. *Scope: guests invited by email (D12).* | SC1 |
| **G2** | A guest can revise their own reply at any time before the event, without involving the host. | SC2 |
| **G3** | A guest always knows their own standing — whether they hold a place or not. | SC3 |
| **G4** | When a capacity is set, guests holding places never exceed it, under any pattern of replies including simultaneous ones. | SC4 |
| **G5** | When a capacity is set, a relinquished place does not sit unused while anyone would still take it. | SC5 |
| **G6** | The guest list becomes final at a moment fixed in advance, with no action by the host at that moment. | SC6 |
| **G7** | The host can end replies early, and that decision holds for every guest at once — and if the event is cancelled, every invited guest is told (D10). | SC7 |

### Non-goals

Each names the argument it exists to defeat. ⚠️ marks the three a reviewer is most likely to challenge for this use case.

| # | Excluded | Why | Defeats the argument |
|---|---|---|---|
| **N1** | **Accounts, passwords and login — for anyone.** Guests do nothing but open their link. Hosts confirm one email address and nothing more *(revised by D9)*. | Replying must not be harder than the channels it replaces. | "We need to know who actually replied." The problem is guests not replying, not guests impersonating each other. **The identity gap this leaves is recorded, not solved.** |
| **N2** ⚠️ | Plus-ones or per-guest party sizes | Counting seats instead of people makes G5 undecidable: when one place frees and the next guest needs two, order stops meaning anything or the place is wasted. | "Every wedding has plus-ones." A real limitation for this use case. |
| **N3** ⚠️ | Co-hosts or shared ownership | The problem is consolidation in one head, not *which* head. | "A couple plans a wedding together." Excluded for cost, not because it is unreasonable. |
| **N4** | Messaging, chat or Q&A between host and guests | Puts the host back in the position the problem identifies as the cause. | "While we have their attention…" |
| **N5** | Meal choices, dietary needs, seating, any other guest attribute | Logistics that become tractable only after the count is trustworthy. | "We already have the list." Every attribute must stay correct across reply changes. |
| **N6** ⚠️ | Automatic reminders to guests who have not replied | SC1 requires a count without contacting anyone; chasing, even automated, is chasing. *Promotion notices (D3) are not reminders — they report a change the guest did not cause.* | "Non-response is the real problem." The most defensible challenge in this list. |
| **N7** | Public discovery, search, or open sign-up | The problem assumes a fixed, known guest list. | — |
| **N8** | Payments, ticketing, refunds | Money appears only as something the host commits to suppliers. | — |
| **N9** | Recurring events | Nothing in the problem recurs. | — |
| **N10** | Cross-event views or reporting | The host organises one event, not a programme. | — |
| **N11** | Guests without their own email address | One guest is one address is one place (N2). The host handles such guests directly, by phone (D12). | "Grandma has no email." She is counted by the host, outside the system. |

---

## Context and Constraints *(Step 5)*

Only constraints that rule out an option or strongly shape the architecture. The scaffold is empty, so each is a floor, never inherited baggage.

### Technical

| # | Constraint | Rules out |
|---|---|---|
| **T1** | **Nothing in the stack holds a session.** No security starter; no session, token or cookie mechanism. *(`hero-backend/pom.xml`)* | Any design where identity is established once and remembered. Identity must travel with each request. |
| **T2** | **Stored state is effectively permanent; derived state is free.** `ddl-auto: update`, no migration tool — columns cannot be renamed or retyped, nothing is backfilled, and constraints are created once. *(`application.yml`)* | Storing any value whose definition might change — counts, orderings, computed statuses. **The strongest architectural pressure in the project.** |
| **T3** | **One process, one database.** *(`start.ps1`)* | In-process locking or in-memory state as the basis of correctness — it would work today and be silently wrong with a second instance. Correctness belongs in the database. |
| **T4** | **No outbound communication plumbing.** No mail starter, no queue, no worker. | Any design where telling someone something is free. Every outbound message is a capability to be built. |

### Product

| # | Constraint | Rules out |
|---|---|---|
| **P1** | **Human scale, one event, one private host.** Tens of guests, at most low hundreds. | Every design justified by scale — caching, replicas, stored counters, eventual consistency. |

### Operational

| # | Constraint | Rules out |
|---|---|---|
| **O1** | **Nothing is supervised and nothing is observed.** Two processes launched by `start.ps1`, restarted by nothing; observability is `show-sql: true`. *(Corrected in Step 15.)* | (a) **Separate** processes that must keep running — nothing restarts them. Work scheduled *inside* the application is permitted **only if its backlog is visible in-band**. (b) *"We will monitor it"* as the mitigation for any risk. |

### Organizational

| # | Constraint | Rules out |
|---|---|---|
| **R1** | **One person is the developer and the operator.** | Any design whose correctness depends on someone tending it. |

**Recorded but not design-shaping:** the README states React 19 while `package.json` pins `^18.3.1`; there is no validation starter. Neither changes any decision here.

---

## Facts, Assumptions, and Open Questions *(Step 6)*

### Confirmed facts

| # | Fact | Source |
|---|---|---|
| **F1** | An event has a title, description, date/time, location and an **optional** max-capacity; its creator is the host. | README brief |
| **F2** | Each invitee receives a unique link and replies Yes, No or Maybe. | README brief |
| **F3** | Once capacity is reached, a further "Yes" is waitlisted, not refused. | README brief |
| **F4** | When a confirmed attendee changes to No, a waitlisted attendee is promoted. | README brief |
| **F5** | A reply may be changed at any point before the event starts. | README brief |
| **F6** | After the event start time, all replies are locked. | README brief |
| **F7** | The host may cancel the event, or close it to further replies, at any time. | README brief |
| **F8** | The host sees live counts and a list of attendees. | README brief |
| **F9** | No authentication or authorization infrastructure exists. | `pom.xml` |
| **F10** | Schema is managed by `ddl-auto: update`; no migration tool. | `application.yml` |
| **F11** | The application runs as one process against one database. | `start.ps1` |
| **F12** | A WhatsApp vendor is wired in configuration while the brief specifies email. | `application.yml` vs README |
| **F13** | The backend has no entities, repositories or controllers. | `hero-backend/src` |
| **F14** | The host is a private individual organising a single event, with no IT support. | Problem owner ¹ |
| **F15** | **The primary case sets no capacity.** A host invites a known list — possibly several hundred people — and wants to know how many confirm. Capacity is the exception. | Problem owner ¹ |

¹ *Stakeholder input from one problem owner, not independently verified. Treated as fact because the design is built for that owner; F15 in particular carries a lot of weight — it makes A3 the main path and lowers the priority of Q2. If it does not generalise, see Assumption failures.*

### Decisions

| # | Decision | Reasoning | Accepted cost |
|---|---|---|---|
| **D1** | **Invitations are delivered by email.** The WhatsApp configuration is deleted, not disabled. | The brief is the requirement; the WhatsApp wiring is residue from an unrelated application. | If wrong, the guest record and delivery path need rework. |
| **D2** | **Leaving Confirmed for "Maybe" releases a place**, exactly as leaving for "No" does. | Otherwise "Maybe" holds a place without commitment — the uncertainty G1 exists to remove. | A hesitating guest loses their place and re-enters at the back. Reliability of the count is chosen over fairness to the hesitant. *Extends F4, which names only "No".* |
| **D3** | **A promoted guest is told.** A promotion notice is recorded in the same all-or-nothing step as the promotion and sent through the outbox (D8). It is sent only if still true when its turn comes. *(Revised in Step 15 — originally silent.)* | Silent promotion produced a guest counted as coming who never knew (X11). The original reason for silence — no way to send — was removed by D8. | A notice sent close to the event may be read too late. |
| **D4** | **Guest and Reply are separate records**, one writer each. A guest with no Reply is *Pending*. | One record with two writers leaves nothing structural preventing either from overwriting the other. | Counts combine two records — negligible at P1 scale. |
| **D5** | **An email address is invited at most once per event**, compared after normalisation. A repeated address is skipped individually, never failing the batch. | Prevents one person holding two places — and makes the host's largest operation, a batch of hundreds, safe to retry. | — |
| **D6** | **Between two tabs of the same guest, the last submission wins.** No version check. | A version check would also refuse a guest's own "No" after a promotion changed her record without her knowing. | A guest's earlier intention can win if the network reorders two submissions. |
| **D7** | **Closed may become Cancelled.** Cancelled is the only terminal state. | A host who closes replies and then has to call the event off must be able to. | — |
| **D8** | **Outgoing email goes through an outbox, drained by a scheduled task inside the application.** | Sending hundreds of messages inside one request fails at the normal size (Step 13); the outbox makes sending paced, resumable, and visible. | One scheduled task; at-least-once delivery. |
| **D9** | **The host verifies their email address.** The management link is sent there; **confirming from it** verifies the host; invitations cannot be sent before that. A lost link is reissued to the same address. | The only way to give a host a way back in (Q4), and to make invitations attributable (Q8). | Host onboarding depends on email arriving. |
| **D10** | **Guests are told when an event is cancelled.** A cancellation notice is recorded for every guest whose invitation was sent, in the same all-or-nothing step as the cancel, and sent through the outbox. *(Step 18.)* | The same reasoning as D3, more strongly: a guest who is not told about a change they did not cause will not act on it — here, hundreds of people travelling to an event that is not happening. | A burst of notices from the one shared sending identity (RD-6). |
| **D11** | **After Close, no reply can change — including a decline.** *(Step 18.)* | Close is what the brief says it is (F7): the host closes the list to stop it moving before committing to suppliers. | From close onward the count can only be **too high**: a guest who can no longer come must tell the host directly. |
| **D12** | **Guests without their own email address are outside the system; the host handles them by phone.** *(Step 18.)* | Keeps one guest = one address = one place (N2), which keeps counting and the waitlist simple. | The system's count covers emailed guests only; the host adds the rest by hand — a small part of the original manual work remains. |
| **D13** | **No email provider for this task (T4).** The drain hands each message to a **development sender** that writes the recipient, the kind and — for invitation and management-link messages — the link to the application console. No real email is sent. *(Before Stage 2.)* | Stages 2–3 can be built and tested end to end without spending a sender reputation (Rollout: "the drain cannot tell a test from a wedding"). The sender sits behind one interface, so a real provider replaces it without touching the drain. | The console now holds working links (see Step 11 → Logs). Every provider-specific behaviour — acceptance, errors, rate limits — is simulated, not observed. |
| **D14** | **Per-host invitation limit (Q8): at most 1,000 invitations per verified host address in any rolling 24 hours, across all of that host's events.** A batch that would exceed it is **rejected whole**, with a clear message. **What counts:** every invitation message created for that address's events, **including resends** (U10) — each is an email sent; addresses skipped as already invited (D5) do not count, and neither do automatic retries of the same message (D15). *(Before Stage 2.)* | Covers the normal 600-guest event (F15) with room to spare, while limiting a host who opens many events to multiply their allowance (W9). Rejecting whole keeps the host's list either fully invited or not at all — never an arbitrary prefix of it. | A host with more than 1,000 guests must spread invitations over two days. A determined abuser with fresh addresses is still only slowed (W9). |
| **D15** | **Automatic retry of failed sends (Q10): a failed send is retried up to 3 times, with increasing delay between attempts; after the last retry fails, the message is final *failed*.** Delays: **1, 5 and 15 minutes** — 3 retries, **4 attempts in total**. A timed-out send counts as a failed attempt. *(Before Stage 2.)* | Most provider failures are transient; retrying inside the drain spares the host from resending by hand. A bounded count keeps a permanently bad address from being retried forever. | A message can take several delays to reach final *failed* — the host sees it as still pending until then. An invitation retried after its link was stored gets a new link on each attempt (KD13); only the newest works. |
| **D16** | **Start-time entry (Q5): the host enters the start time in their browser's time zone; the browser converts it and sends an absolute instant.** The server stores and compares only that instant. *(Before Stage 2.)* | The host means the time where they are, and the browser is the only place that knows that zone. The server never interprets a local time, so G6 and INV-B7 compare like with like. | A host creating an event from another time zone than the venue's enters it in their own zone; the page must make the zone visible. The start time still cannot change after creation (W4). |### Working assumptions

| # | Assumption | If wrong |
|---|---|---|
| **A1** | Only "Yes" consumes a place; "Maybe" does not. | The capacity rule changes shape. |
| **A2** | The waitlist is first-in-first-out by the time a guest entered it. | Promotion needs a ranking model; ties become visible. |
| **A3** | **No capacity means no waitlist — every "Yes" is confirmed.** This is the primary path (F15). | The ordinary case inherits machinery it has no use for. |
| **A5** | Close means final **for replies — including declines (D11)**; Cancel means final **for the event**. *(Revised by D7.)* | The event state model changes. |
| **A6** | Links are durable and reusable until replaced. | Expiry becomes a core mechanism. |
| **A7** | A waitlisted guest leaving releases nothing. | Promotion fires on departures that free no place, over-filling the event. |
| **A8** | The number invited is independent of capacity — a host may invite more people than the room holds. | Invitation needs validation it does not have. |

*(A4 became D2.)*

### Open questions

| # | Question | Blocks |
|---|---|---|
| **Q2** | What happens when a host **lowers** capacity below the number confirmed — or **adds** a capacity to an event that had none and already has more confirmed than the new limit? Each answer breaks a different promise: the host's control, a guest's place, or G4. | The capacity-edit path, which does not exist until this is answered. |
| **Q6** | What may a guest see beyond their own state — queue position, totals, other guests? | The guest view. |
| **Q9** | A host who has lost their management link has no link to name the event with. **How do they identify which event to recover?** Whatever the answer, a reissue request must not let a stranger invalidate a working host's link. | The reissue flow (U9). |

### Resolved questions

Kept so the reasoning trail stays visible.

| # | Question | Resolved by |
|---|---|---|
| **Q1** | Email or WhatsApp for invitations? | **D1** — email |
| **Q3** | Is a promoted guest told, or do they find out by returning? | **D3** — told, through the outbox |
| **Q4** | How does a host regain access without an account? | **D9** — reissue to the verified address *(its entry point is Q9)* |
| **Q5** | Which time zone does the host mean when entering the start time? | **D16** — the browser's; it sends an absolute instant |
| **Q7** | How is a guest reached whose invitation failed? | **D8** — every message has a visible status; resend creates a new one |
| **Q8** | What is the per-host invitation limit? | **D14** — 1,000 per verified host address per rolling 24 hours, across events; an exceeding batch is rejected whole |
| **Q10** | Are failed sends retried automatically? | **D15** — up to 3 retries with increasing delay, then final *failed* |

---

## Actors and Workflows *(Step 7)*

### Actors

| Actor | Identified by | May do |
|---|---|---|
| **Host** | Holding the event's management link | Create, invite, resend, view status, close, cancel |
| **Guest** | Holding their own link | Reply and revise **their own** reply |
| **System** | — | Enforce capacity, promote, lock, send queued messages |
| **Email provider** | — | Deliver messages; holds each link in transit |
| **Link holder** *(unintended)* | Indistinguishable from the guest | Anything the guest may do — a known gap under N1 |

State names used below: **Event** Open · Closed · Cancelled. **Reply** *(none = Pending)* · Confirmed · Waitlisted · Declined · Maybe.

### User-facing flows

| Flow | Trigger | Major steps | State changes | Depends on |
|---|---|---|---|---|
| **U1** Create event | Host submits details and their email | Validate; start time in the future; create event; queue the management-link message | Event **Open**, host **unverified** | D9, D16 |
| **U8** Verify host | Host opens the management link **and explicitly confirms** | Resolve link; mark host verified | Host **verified** | D9 — a page load alone never verifies (E6) |
| **U2** Invite guests | Host submits addresses | Resolve management link; require verified host; **require the event Open and not started**; create each guest with an invitation message, skipping existing addresses | Guests created; messages queued | D5, D8, D9, Q8 |
| **U3** First reply | Guest submits Yes/No/Maybe | Resolve link; lock check; capacity decision if "Yes" and capacity set; record; show outcome | Pending → Confirmed / Waitlisted / Declined / Maybe | A1, A3 |
| **U4** Change reply | Guest submits a different reply | Resolve; lock check; release or request a place; promote if released; record | See transition table | D2, A7 |
| **U5** View status | Host opens event | Resolve management link; compute counts; list guests; show message backlog and failures | None | KD12 |
| **U6** Close or cancel | Host chooses | Resolve; state guard; set status; **on cancel, record a cancellation notice for every guest whose invitation was sent** | Open → Closed / Cancelled; Closed → Cancelled | D7, D10, D11 |
| **U7** Check own standing | Guest opens link | Resolve; show event and own state | None | Q6 |
| **U9** Recover management link | Host requests a new one | Queue a new management-link message to the verified address | Old link replaced when the new one is sent | D9, **Q9** |
| **U10** Resend invitation | Host resends a failed or unsent invitation | Queue a new invitation message | None until sent | D8 |

### Internal system flows

| Flow | Trigger | What it does |
|---|---|---|
| **S1** Capacity decision | "Yes" in U3/U4, capacity set | Confirmed if a place is free, otherwise Waitlisted |
| **S2** Promotion | A place released in U4, capacity set | Earliest waitlisted → Confirmed; records a promotion notice. **The only write to a guest who did not act.** |
| **S3** Lock check | Every U3/U4 | Refuse if Closed, Cancelled, or started |
| **S4** Link resolution | Every action | Resolve to one event, or one *(event, guest)*; otherwise one identical refusal |
| **S5** Send one message | Drain picks a queued message | Mark it *sending*; re-check it is still true; **for invitation and management-link messages only**, generate the link (KD13); hand to the provider; mark the result. **Notices carry no link.** |

### Background flows

| Flow | What happens | Notes |
|---|---|---|
| **B1** Event start passes | Nothing is performed. From that moment S3 refuses replies. | A rule, not a process (KD4). |
| **B2** Outbox drain | A scheduled task inside the application claims a few queued messages and runs S5 on each. | The only autonomous execution in the design (KD3). Its backlog is shown to the host (O1). |

### Failure flows

| # | Situation | Strikes | Handled in |
|---|---|---|---|
| **X1** | Two "Yes" for the last place | S1 | Concurrency — **the scenario the task requires** |
| **X2** | Two confirmed guests leave at once | S2 | Concurrency |
| **X3** | Reply at the start boundary | S3 | Concurrency (KD8) |
| **X4** | Reply racing a cancel | S3, U6 | Concurrency |
| **X5** | Retried submission | U3/U4 | Concurrency |
| **X6** | One guest, two tabs | U4 | D6 |
| **X7** | Message accepted by the provider but never delivered | S5 | Risk RD-1 |
| **X8** | Unknown link, or a link from another event | S4 | Trust boundaries |
| **X9** | Guest forwards their link | S4 | Known gap under N1 |
| **X10** | Host loses the management link | U1 | D9, Q9 |
| **X11** | Promoted guest never learns of it | S2 | **Closed by D3**, subject to X7 |
| **X12** | The drain stalls | B2 | Visible as a growing backlog in U5 |
| **X13** | A queued message is no longer true when its turn comes | S5 | Skipped (D3) |
| **X14** | The host's own management-link email never arrives | U1 | Risk RD-5 |
| **X15** | A cancelled event sends hundreds of notices at once | U6, B2 | Paced by the drain; RD-6 |

---

## Invariants *(Step 8)*

The tenant is **the event**: under N1 there are no accounts, so the event is the natural unit of isolation.

### Business

| # | Invariant | Threatened by | Control |
|---|---|---|---|
| **INV-B1** *Capacity* | When a capacity is set, Confirmed guests never exceed it. | X1; Q2 | Count and write under one per-event lock; no capacity-edit path until Q2 |
| **INV-B2** | No capacity → nobody is ever Waitlisted. | — | Capacity decision skipped entirely |
| **INV-B3** *Promotion* | A released place with a non-empty waitlist promotes **exactly one** guest — the earliest. | X2 | Promotion inside the same locked, all-or-nothing step as the release |
| **INV-B4** | A waitlisted guest leaving triggers no promotion. | — | Transition table |
| **INV-B5** | Only "Yes" occupies a place; leaving Confirmed for No or Maybe releases it. | — | Transition table |
| **INV-B6** | A waitlisted guest reaches Confirmed only through promotion; repeating "Yes" changes nothing, including position. | — | Transition rule; entered-state time written only on an actual change |
| **INV-B7** *Lock after start* | Once started, Closed or Cancelled, no reply is created or changed. | X3, X4 | Checked inside the locked step, with "now" read after the lock (KD8) |
| **INV-B8** | Cancelled is terminal; Closed may only become Cancelled; nothing returns to Open. | — | State guard |
| **INV-B9** | An event's start time is in the future when it is created. | — | Validation against the same clock as INV-B7 |
| **INV-B10** | A promotion and its notice exist together or not at all. | Partial failure | Recorded in the same all-or-nothing step |
| **INV-B11** | A queued message is sent only if it is still true when sent. | X13 | Re-checked by S5 at send time |
| **INV-B12** | A cancellation and its notices exist together or not at all. | Partial failure | Recorded in the same all-or-nothing step as the cancel |
| **INV-B13** | A notice never carries or replaces a link. | W1 | Only invitation and management-link messages generate links |

### Data integrity

| # | Invariant | Control |
|---|---|---|
| **INV-D1** | Every guest belongs to exactly one existing event. | Referential constraint in the database |
| **INV-D2** | Every guest has at most one Reply. | Uniqueness in the database; replies update, never add |
| **INV-D3** | Every link is unique and cannot be derived from any other. | Cryptographic randomness + uniqueness |
| **INV-D4** | Every count shown equals the records in that state at that moment. | Counts derived, never stored (KD5) |
| **INV-D5** | The waitlist has one total order. | Entered-state time plus a unique tie-breaker |
| **INV-D6** | An email address appears at most once per event. | Uniqueness on *(event, normalised email)* — D5 |

### Authorization

| # | Invariant | Control |
|---|---|---|
| **INV-A1** | Only the holder of an event's management link acts as its host — **including viewing status**. | One mandatory check at the Access Gate |
| **INV-A2** | A guest link affects only that guest's own reply. | Target always derived from the link, never from the request |
| **INV-A3** | The promoted guest is chosen by the system. | Server-side selection only |
| **INV-A4** | A refused link reveals nothing — not whether the event exists, nor why. | One identical refusal, in content and in time |
| **INV-A5** | A guest never sees another guest's email address. *(Anything else a guest may see: Q6.)* | A separate, minimal guest view |
| **INV-A6** | No invitation can be sent for an event whose host is unverified. | Checked in U2 (D9) |
| **INV-A7** | At most one link per holder works at any time. | One Link record per holder, replaced in a single change (KD13) |

### Concurrency

| # | Invariant | Threatened by | Control |
|---|---|---|---|
| **INV-C1** | INV-B1 holds under simultaneous "Yes". | X1 | Per-event lock |
| **INV-C2** | Each released place causes exactly one promotion under simultaneous releases. | X2 | Per-event lock |
| **INV-C3** | The lock decision and the reply it permits are one step. | X3 | Inside the lock; clock read after it |
| **INV-C4** | A reply is entirely before or entirely after a close or cancel. | X4 | Close and cancel take the same lock |
| **INV-C5** | Submitting the same reply twice equals submitting it once. | X5 | Idempotent "set reply to X" |
| **INV-C6** | A release, its promotion and its notice happen together or not at all. | Partial failure | One transaction |

*INV-C7 (no overwrite from a stale view) was dropped by D6.*

### Tenant isolation

| # | Invariant | Control |
|---|---|---|
| **INV-T1** | No data of one event is reachable through another event's link. | Every lookup scoped by event |
| **INV-T2** | A link is valid only within the event it was issued for. | Resolution to an *(event, guest)* pair |
| **INV-T3** | Counting and promotion never involve another event's guests. | Scoped decisions |

### Where invariants fall short

- **INV-B1 vs Q2.** Changing capacity below the confirmed count breaks either INV-B1 or a guest's confirmed place. No answer preserves both.
- **INV-B3 and INV-B10 hold in the data; the room depends on email.** A notice that is sent but never read (X7) leaves a confirmed guest who does not come.
- **INV-A2 against a forwarded link.** Whoever holds the link *is* the guest, as far as the system can tell (X9).

---

## Architecture *(Step 9)*

### Components

Each is stated with what breaks without it.

**1. Access Gate** — every request enters here. A management link resolves to exactly one event, a guest link to exactly one *(event, guest)*; anything else receives one identical refusal. The two kinds of link are never interchangeable.
Two requests carry no link by nature — creating an event (U1) and asking to recover a management link (U9). They enter through the gate's **open path**, which permits only those two actions and never resolves to, reads, or reveals any existing event.
*Without it:* every action carries its own check, and the first one forgotten — most likely on the host's status read — leaks every guest's email address.

**2. Event Management** *(host side)* — create (U1), verify (U8), invite (U2), resend (U10), recover (U9), view status (U5), close or cancel (U6).
Owns the event, its status, and each guest's identity.
*Without it:* nothing guarantees an event cannot be reopened, and guest identity has no single writer.

**3. Reply Engine** *(guest side)* — reply (U3), change (U4), view own standing (U7). Contains the lock check, the capacity decision, promotion and the transition table. **The only component that makes a capacity decision or promotes.**
Owns each guest's Reply.
*Without it:* counting, writing and promotion end up in different places, and INV-C1, C2 and C6 cannot hold.

**4. Outbox Drain** — the scheduled task inside the application (B2). Claims a few queued messages, re-checks that each is still true, generates a link for invitation and management-link messages only (KD13), hands it to the email provider with a timeout, and records the result.
Owns every **Link** record and every message's **status**.
*Without it:* messages are sent inside requests, which fails at the normal batch size, and nothing can send a promotion notice.
*Note:* an earlier draft removed a delivery component because it owned nothing and contained no logic. This one exists because it now has both.

### Dependencies with obligations

**PostgreSQL** provides constraints, the authoritative clock, and a per-event locking mechanism. **It does not make anything correct by itself:** correctness depends on every state-changing path *taking* the lock. Exactly two paths do — every reply change (Reply Engine) and every status change (Event Management). A third path added later that does not would silently break INV-C1 to C4.

**Email provider** receives every outgoing message. The system can observe whether the provider **accepted** a message — never whether it **arrived**.

### Flow boundaries

```
Host Console ──► Access Gate ──► Event Management ──┐
                                                    ├──► outbox ──► Outbox Drain ──► Email provider
Guest Page ────► Access Gate ──► Reply Engine ──────┘

         every component ──► PostgreSQL (constraints · per-event lock · clock)
```

| Path | Reaches | Never reaches |
|---|---|---|
| Host | Event Management | Reply Engine |
| Guest | Reply Engine | Event Management |

The two paths share only the Access Gate, the outbox, and the per-event lock. A guest cannot reach host capabilities because no route exists — not because a permission check says no.

### Key design choices

| # | Choice | From |
|---|---|---|
| **KD1** | Split by who acts — host or guest — with every capacity decision inside one component. | INV-C1 to C6 |
| **KD2** | Correctness lives in the database: a per-event lock taken by every state change. | T3, F11 |
| **KD3** | No separate processes. **Exactly one** scheduled task inside the application — the outbox drain — with its backlog visible to the host. *(Revised in Step 15.)* | O1 (corrected), R1, D8 |
| **KD4** | The lock at start is a **rule** checked per request, not something that fires at start time. KD3 does not license a second scheduled task. | T2, B1 |
| **KD5** | Derive, don't store: counts, waitlist order, locked-ness. | T2, INV-D4 |
| **KD6** | One gate for all access. | N1, F9, T1 |
| **KD7** | Two surfaces with disjoint paths. | INV-A2, A5 |
| **KD8** | "Now" comes from the database **and is read after the per-event lock is acquired** — never the transaction's start time. *(Corrected in Step 12.)* | T3, INV-B7, INV-B9 |
| **KD9** | *Replaced by D8.* | — |
| **KD10** | One outbound path: the outbox. Management links, invitations, promotion notices and cancellation notices all go through it. | D3, D8, D9 |
| **KD11** | Nothing added for scale — no caching, no replicas. | P1 |
| **KD12** | Each count has one definition, used by both the capacity decision and the host view. | INV-D4 |
| **KD13** | **A link is generated at the moment its message is sent; only its protected form is stored.** Notices carry no link (INV-B13). *Proposed in Step 16; **confirmed by the problem owner** before Stage 2.* | Step 11 vs D8 |

---

## Data Ownership and State Model *(Step 10)*

### Records

| Record | Source of truth | Written by | Stored | Derived |
|---|---|---|---|---|
| **Event** | Event record | Event Management only | Title, description, location, start time, optional capacity, status, host email, whether verified | *Locked* |
| **Guest** | Guest record | Event Management — **create only** | Event, normalised email | *Pending* = no Reply |
| **Link** | Link record — one per holder: the event's host, or a guest | **Outbox Drain only**, at send time (KD13) | Holder, protected link | — |
| **Reply** | Reply record | **Reply Engine only**, inside the per-event lock | Guest, state, the moment the guest entered this state | — |
| **Outbound message** | Message record | **Created** by the component that owns its cause — Event Management for management links, invitations and cancellation notices; Reply Engine for promotion notices. **Status written only by the drain.** | Kind — management link · invitation · promotion notice · cancellation notice; event; recipient; status: queued · sending · sent · failed · skipped | — |

**Event nullability in the database** *(Stage 1 decision)*: title, start time, status, host email and verified are `NOT NULL`; description and location are nullable in the database and required by Event Management's validation (Stage 2). Under T2 a `NOT NULL` rule cannot be added or removed later, so requiredness that might change is enforced in code, not the schema.

Event fields other than status have **no edit path**. That is why Q2 cannot yet arise, and why a start time cannot move — by omission, not decision.

A resend (U10) or recovery (U9) **creates a new message** rather than resetting an old one, so the drain remains the only writer of message status.

**Message states:** queued → **sending** (claimed by the drain) → sent · failed · skipped. A message left in *sending* longer than the send timeout — because the drain crashed — returns to *queued*; that is where at-least-once delivery comes from. A failed attempt with retries left returns to *queued* to wait for its next attempt; *failed* is final only after the last retry (D15).

**Why links are their own record.** Under KD13 a link is created by the drain, at send time. Storing it on the Event or Guest record would give those records a second writer — the exact problem D4 was made to avoid. As a separate record, every record still has exactly one writer, and replacing a link touches nothing else. The Access Gate reads Link records to resolve every request.

### Event state machine

```
                 ┌──── close ────►  CLOSED
                 │                     │
  created ──►  OPEN                  cancel
                 │                     │
                 │                     ▼
                 └──── cancel ───►  CANCELLED   (terminal)
```

**Locked** is not on this diagram because it is not a state. It is a rule: `status ≠ Open` **or** the database clock, read after the lock, has reached the start time.

### Reply transitions

| From | Trigger | To | Releases a place? |
|---|---|---|---|
| *Pending* | Yes — no capacity, or a place free | Confirmed | — |
| *Pending* | Yes — full | Waitlisted | — |
| *Pending* | No / Maybe | Declined / Maybe | — |
| Confirmed | No or Maybe | Declined / Maybe | **Yes → promotion + notice** (D2, D3) |
| Confirmed | Yes | *unchanged* | — |
| Waitlisted | No or Maybe | Declined / Maybe | No (A7) |
| Waitlisted | Yes | *unchanged — position kept* | — (INV-B6) |
| Waitlisted | *promotion* | Confirmed | **System only** |
| Declined / Maybe | Yes | Confirmed or Waitlisted | Back through the capacity decision |
| Declined ↔ Maybe | No / Maybe | the other | No |
| *any* | *anything, while locked* | *refused* | (INV-B7) |

**Entered-state time** is written on every actual change of state and never on a no-op. A guest going Waitlisted → Maybe → Yes therefore re-enters at the back — the direct consequence of D2.

### Derived state

| Concept | Derived from | Why not stored |
|---|---|---|
| Pending | Guest with no Reply | Absence is the fact |
| Locked | Status + clock + start time | Would need something to set it on time (KD4) |
| Counts per state | Replies grouped by state | A stored counter drifts (INV-D4) |
| Waitlist order | Entered-state time + tie-breaker | Stored ranks cannot be redefined (T2) |
| Places remaining | Capacity − confirmed | Derived from derived values |
| Message backlog | Queued and failed messages | Shown to the host so the drain's health is visible (O1) |

### Correctness review

| Risk type | Where it arose | How it is closed |
|---|---|---|
| **Stale read** | Lock, count, and promotion evaluated before the change | All read inside the per-event lock |
| **Stale read** *(human)* | Guest page says "open"; it locks before they submit | Write rechecks and refuses with a reason the guest can understand |
| **Duplicate write** | Retried reply; double-click first reply; batch of 600 retried | Idempotent reply; one Reply per guest; D5 per-address skip |
| **Conflicting update** | Close vs cancel; a guest declining as she is promoted | Per-event lock orders them — both orders give the right result |
| **Conflicting update** | One guest, two tabs | Accepted — D6 |
| **Mutation authority** | Identity vs reply on one guest | D4 — separate records |
| **Mutation authority** | One count, two places computing it | KD12 — one definition |
| **Mutation authority** | Message status written by resend and by the drain | Resend creates a new message; only the drain writes status |
| **Mutation authority** | Links created by the drain, on records owned by Event Management | A separate Link record, written only by the drain |

---

## Trust Boundaries and Security Notes *(Step 11)*

Every trust decision rests on possession of a link (N1). There is no session and no second factor.

### Where trust enters

| # | Entry | Component · flow | Why it matters here |
|---|---|---|---|
| **E1** | Management link | Access Gate → Event Management | Arrives only by email to the verified address (D9) |
| **E2** | Guest link | Access Gate → Reply Engine | Has passed through the provider and the guest's mail system before the guest sees it |
| **E3** | **No credential** | Event Management · U1, U9 | Anyone can create an event, and anyone can ask for a management link to be recovered (Q9) |
| **E4** | Host-authored text — title, description, location | U1 → U7 and every outgoing message | Under E3, text written by a stranger is shown on a page guests trust and in email from this system's address. Always treated as text, never as markup. |
| **E5** | Email provider | Outbox Drain | Holds every recipient's address **and** a readable, working link in each message |
| **E6** | Recipient's mail system | Outbound messages | May open links automatically to scan them |

**Opening a link never changes state.** U7 displays; only an explicit submission reaches U3/U4. Otherwise a mail scanner at E6 would reply on the guest's behalf — and a scanner opening a host's management link would count as verification (U8), which is why verification requires an explicit action on the page, not the page load alone.

### How links leak, in this system

| Path | Status |
|---|---|
| Forwarding (X9) | Undetectable under N1 — accepted gap |
| Automatic scanners (E6) | Harmless only because opening ≠ submitting |
| Resources loaded by the guest or host page | The page URL **is** the credential; the pages load no third-party resources |
| Logs | Hibernate `show-sql` prints statements **without bound values**. **The development sender (D13) prints every working link to the console by design** — acceptable only because no real guest uses it; it is removed with the choice of a real provider. Adding request-URL logging would add a second leak. |

### Protected storage — and what it forces

Links are stored only in protected form, so reading the database yields no working credential. Because a link must be looked up by its value, the protection is deterministic — safe only because links are long and random.

This has direct consequences:
- **A stored link can never be shown or sent again** — only replaced. Resend (U10) and recovery (U9) must issue new links.
- **The outbox cannot hold a readable link** waiting to be sent. Hence **KD13**: a link is generated at send time.
- **Even the operator cannot look up a lost link.** Recovery is only ever reissue.

### Where authorization is enforced

| Point | Rule |
|---|---|
| Access Gate — link kind | A guest link is never accepted where a management link is expected, or the reverse |
| Access Gate — refusals | Identical for *not found*, *wrong kind* and *wrong event* — including in time, since the three take different paths through S4 |
| Event Management — U5 | Checked exactly like U6; it changes nothing but returns every guest's email, and is also structurally unreachable from the guest path |
| Event Management — U2 | Refused while the host is unverified (INV-A6), or once the event is Closed, Cancelled or started |
| Access Gate — open path | Permits only U1 and U9; never resolves, reads or reveals an existing event |
| Reply Engine — U3/U4 | The guest changed is always the one S4 resolved |
| Reply Engine — S2 | The promoted guest is chosen by the system |
| Reply Engine — lock | "Now" comes from the database, so no request can influence it |

### Where tenant scope matters

- S4 resolves a link to an *(event, guest)* pair, never to a match anywhere in the database.
- S1 and S2 count and select within one event. A mis-scoped promotion would move a guest from someone else's event into this one.
- The per-event lock isolates events from each other — but **within** an event it is shared. Anyone holding one guest link can occupy it with repeated submissions, delaying every other guest and the host's cancel. Low impact at P1 scale; a rate limit at the Access Gate is the only place a control can live.
- Internal database identifiers never appear in URLs.

### Sensitive data

| Data | Exposed through |
|---|---|
| Guest email addresses | U5 only; the provider (E5) |
| Management link | Email to the verified address |
| Guest links | Invitation email, and wherever it is forwarded |
| **Event location and date** | U7 and every invitation — **for a private event this tells a stranger where a party is and when a family is away from home**, including anyone holding a forwarded link |
| Reply states | U5 |
| Vendor API key committed to `application.yml` | The repository and its public upstream history. Deleted from configuration under D1; rotation requires the vendor-account owner. |

### Privileged operations

| Operation | Why privileged |
|---|---|
| **Cancel** (U6) | The only terminal transition; affects every guest |
| **Invite** (U2) | Makes this system send email to addresses the caller chooses |
| **View status** (U5) | Bulk disclosure of personal data |
| **Recover management link** (U9) | Unauthenticated by nature (E3). A request from a stranger must not be able to invalidate the real host's working link — a constraint on any answer to Q9. |
| **Promote** (S2) | System only; the one write to a guest who did not act |

### The relay risk (Q8)

U1 (no credential) → U2 → the outbox → email from this system's address. D9 makes each chain attributable to a verified address, so it **can** be limited per host. Without a limit, one verified host could send without bound — and once the sending address is flagged as spam, **every host's messages stop arriving**, silently. **D14 sets the limit:** 1,000 invitations per verified host address per rolling 24 hours, across all of that host's events.

---

## Concurrency and Correctness Notes *(Step 12)*

There is no queue for ordering state changes. The ordering a queue would provide — *changes to one event happen one at a time* — is provided by the **per-event lock inside a database transaction**.

### Units of work

| Unit | Inside, in order | Outside |
|---|---|---|
| **Reply** *(U3, U4)* | take per-event lock → read "now" → lock check → capacity decision or release → promotion if needed → write reply(s) → record promotion notice → commit | Nothing. It only *records* a notice; sending happens later. |
| **Status** *(U6)* | take per-event lock → state guard → write status → on cancel, record cancellation notices → commit | Sending |
| **Invite** *(U2, U10)* | create guests, skipping existing addresses → queue messages → commit | Sending. **Takes no per-event lock.** |
| **Send** *(S5, per message)* | mark it *sending* if still queued → re-check it is still true → for invitation and management-link messages, generate the link and store its protected form → hand to provider with a timeout → mark sent / failed / skipped | **Never holds the per-event lock** |

**Why the invite unit takes no lock:** an invite racing a cancel can queue messages for an event cancelled a moment earlier. No invariant is broken — and under INV-B11 those messages are skipped at send time.

**The lock as PostgreSQL takes it** *(Stage 1)*: the per-event lock is a `PESSIMISTIC_WRITE` read of the event row, which Hibernate issues on PostgreSQL as `SELECT … FOR NO KEY UPDATE`, not `FOR UPDATE`. This is sufficient and preferable:
- **It still serialises the event.** `FOR NO KEY UPDATE` conflicts with itself, so any two reply or status units on one event run one after the other. The Stage 1 tests for X1, X2 and X4 prove this; removing the lock makes them fail.
- **It does not block invitations.** Inserting a guest or a message that references the event takes only `FOR KEY SHARE` on the event row, which `FOR NO KEY UPDATE` permits (plain `FOR UPDATE` would block it). The invite unit therefore never waits on a reply burst, as the design intends.

**Why the link is stored before handoff:** if the handoff fails, the message is marked **failed** and visible to the host, who can resend. Storing it after handoff would risk a guest receiving a link that was never stored — a failure only the guest would ever see.

### Vulnerable areas and controls

| Area | What can go wrong | Control class |
|---|---|---|
| **X1** — last place | Both counted a free place | **Per-event lock + transaction** |
| **X2** — simultaneous releases | Same guest promoted twice | **Per-event lock + transaction** |
| Release without promotion or notice | Event left under capacity; guest untold | **Transaction** (INV-C6) |
| **X3** — start boundary | Reply accepted on a stale "now" | **Per-event lock + explicit rule: read "now" after the lock** |
| **X4** — reply vs cancel | Place taken on a cancelled event | **Per-event lock** shared by both |
| Close vs cancel from two tabs | Mixed outcome | **Per-event lock + state guard** |
| Double-click first reply | Two Replies for one guest | **Unique constraint** as backstop; lock makes the second an update |
| Retried reply | Counted twice | **Idempotency** |
| Retried "No" after its promotion ran | Second promotion, second notice | **Idempotency via the transition table** — Declined → Declined is a no-op |
| Repeated "Yes" while waitlisted | Moved to the back | **Explicit rule** on entered-state time |
| Waitlist tie | "Earliest" names two guests | **Explicit tie-breaker** |
| Batch of 600 retried | Duplicate guests | **Unique constraint + idempotency** — per-address skip |
| Resend racing the drain | One status overwrites another | Resend **creates a new message**; only the drain writes status |
| Drain restarts mid-send | Message sent twice | **At-least-once, accepted** — under KD13 the second carries a newer link; only the newest works |
| Provider call hangs | All sending stops | **Explicit timeout** on every call |
| Second application instance | Two drains send the same message | **Claim conditional on queued status** — not needed at one instance (F11) |
| **X6** — two tabs | Earlier intention wins | **Version check considered and rejected** (D6) |
| Leaked guest link flooding one event | Other guests and the host's cancel stall | **Rate limit at the Access Gate** |

### Correction recorded here

**KD8 was not precise enough.** Some database clock functions return the time the **transaction began**. A reply that starts before the event, waits for the lock, and acquires it after the start would then pass the lock check — X3 reintroduced through the clock. The same would misorder the waitlist. "Now" and entered-state time are therefore read **after** the lock is acquired.

---

## Scalability and Multi-Tenancy Notes *(Step 13)*

Figures here are order-of-magnitude reasoning, not measurements.

### Growth axes

| Axis | What grows |
|---|---|
| Guests per event | Outbox backlog per invitation batch; host view size. **600 is normal** (F15). |
| Replies in a burst | Reply units queuing on one event's lock |
| Number of events | Rows; concurrent use of one process and connection pool |
| Total email sent | Load on one provider account and **one shared sender reputation** |
| Time | Past events and their guests accumulate; nothing is deleted |

### Bottlenecks

1. **Sending hundreds of messages inside one request** — the original first bottleneck, at the normal event size. **Resolved by D8.**
2. **Shared sender reputation** — the first cross-tenant bottleneck. Every host sends from the same identity; one host sending to bad addresses, or abusing Q8, degrades delivery for all, silently and slowly.
3. **Not bottlenecks at this scale:** the per-event lock under a burst (a few fast operations per reply); derived counts over hundreds of rows; one process and one connection pool.

### Multi-tenancy

| Resource | Isolated per event? |
|---|---|
| Data | Yes — INV-T1 to T3 |
| Per-event lock | Yes |
| Process, connection pool | No — only at far larger scale |
| Provider quota | No |
| **Sender reputation** | **No — the most important shared resource** |

Hosts are identified only per event, by a verified address (D9). That is the minimum that makes **per-host limits possible at all** — before D9, a noisy neighbour could not even be attributed.

### Sufficient now vs. later change

| Sufficient now | Why |
|---|---|
| Per-event lock; derived state; one PostgreSQL; one process; no caching | Nothing at P1 scale approaches their limits |
| Correctness in the database (KD2) | Already survives a second instance |

| Trigger | Change required |
|---|---|
| Sender reputation degrades, or abuse appears | Set and enforce the Q8 limit; pause the drain (Rollout) |
| **A second application instance** | The drain's claim must be conditional; any rate limit held in process memory must move to shared storage |
| Past events accumulate personal data | A retention rule and a deletion path for whole past events — deleting a locked event releases no places, so it does not bypass Reply Engine |

---

## Risks and Failure Notes *(Step 14)*

Only risks arising from this architecture, these workflows or these assumptions. **Visible?** matters because under O1 there is no monitoring: an invisible failure persists. Risks resolved by later decisions are kept, marked, for traceability.

### Correctness

| ID | Failure | Arises from | Visible? | Status |
|---|---|---|---|---|
| **RC-1** | Replies accepted after the event has started | KD8 + KD2: a reply waits for the lock; "now" read at transaction start is stale | ❌ | Open — an implementation rule |
| **RC-2** | Capacity exceeded, double promotion, reply on a cancelled event | KD2 is an obligation: a future write path that skips the lock | ❌ | Open |
| **RC-3** | Waitlisted guest silently moved to the back | INV-B6 + D2: entered-state time rule | ❌ | Open — precise rule |
| **RC-4** | Promotion picks different guests on different occasions | A2: timestamp order needs a tie-breaker | ❌ | Open — precise rule |
| **RC-5** | Host sees a count the capacity decision never used | Two components computing counts | ❌ | Closed by KD12 |
| **RC-6** | Host cannot invite anyone after one partial failure | D5 + U2: a duplicate failing the whole batch | ✅ | Closed — per-address skip |
| **RC-7** | Guests or hosts appear to act without acting | N1 + email: scanners open links (E6) | ❌ | Closed — opening never changes state |
| **RC-8** | A message sent that is no longer true | D8: time between queuing and sending | ❌ | Closed — INV-B11 |
| **RC-9** | Two working management links at once | D9 reissue | ❌ | Closed — replacement is one change (KD13) |

### Dependency

| ID | Failure | Arises from | Visible? | Status |
|---|---|---|---|---|
| **RD-1** | **Messages never arrive, and nobody knows** | D1: only acceptance is observable | ❌ | **Open — inherent to email** |
| **RD-2** | Sending stops partway through a batch | Inline sending hitting provider limits | ⚠️ | Closed by D8 — paced |
| **RD-3** | **Every host's messages start landing in spam** | D1 + E3 + one shared sending identity | ❌ | **Controlled per address** by D14; still open to fresh addresses (W9) |
| **RD-4** | Working credentials for every recipient exposed together | N1 + D1: each message carries a readable link | ❌ | Open — outside this system |
| **RD-5** | **Host never receives their management link** | D9: host onboarding depends on email | ⚠️ Host sees nothing arrive | Open — U9 recovery is defined, but its entry point (Q9) is open |
| **RD-6** | A cancelled large event sends hundreds of notices at once from the shared identity | D10 | ❌ | Paced by the drain; adds to RD-3 |

### Operational

| ID | Failure | Arises from | Visible? | Status |
|---|---|---|---|---|
| **RO-1** | Host misled about whether invitations were sent | Inline sending outlasting the request | ⚠️ | Closed by D8 |
| **RO-2** | Unknown guests never invited; retry skips them | Crash mid-loop + D5 | ❌ | Closed by D8 — progress is data |
| **RO-5** | Host permanently locked out, and no one can help | N1 + protected storage | ✅ | **Partly closed** — D9 defines reissue, but its entry point (Q9) is still open |
| **RO-6** | One event's guests and host stall | KD2 + one leaked guest link | ✅ | Open — rate limit at the Access Gate |
| **RO-7** | Drain stalls; nothing is sent | B2 | ⚠️ Only in the host's backlog | Open — visible only if the host looks |
| **RO-8** | A stranger invalidates a real host's management link | U9 is unauthenticated | ✅ | Open — a constraint on Q9 |
| **RO-9** | The host's real total mixes the system's count with guests handled by phone | D12 | ✅ to the host | Accepted |

### Assumption failures

| If wrong… | What breaks | Rework |
|---|---|---|
| **F15** — most hosts set no capacity | Q2 and the waitlist race move to the main path | Large |
| **D3** — promoted guests read email in time | A notice read too late; the chair is empty, though the system told them | Small — accepted |
| **A8** — invitations independent of capacity | Adds pressure on Q8 and RD-3 | Small |
| **N1** — no identity beyond a verified host email | Every identity-dependent need returns here | Largest in the design |
| **D2** — losing a place on "Maybe" is acceptable | Guests feel punished for hesitating | Medium |
| **A2** — first-in-first-out | Host wants priority guests | Medium |
| **A6** — links durable until replaced | Expiry becomes core | Medium |
| **A3** — no capacity, no waitlist | The primary path inherits unneeded machinery | Small |
| **P1** — human-scale events | "Later" changes in Step 13 become "now" | Medium |
| **D11** — guests accept that close is final | Declines after close arrive by phone; the count is overstated | Small |
| **D12** — few guests lack their own email | If many do, much of G1 is manual again | Medium |

### Top risks after all decisions

| Rank | Risk | Why |
|---|---|---|
| 1 | **RD-1** (and **RD-5**) | Undetectable by design — now reaching hosts too |
| 2 | **RD-3** | One sender identity shared by every host; the limit that would control it is undecided |
| 3 | **RC-1** | One small implementation detail silently reverses a core guarantee |
| 4 | **RC-2** | The lock is an obligation, not a structure |
| 5 | **RO-7** | The drain's failure is visible only to a host who happens to look |

---

## Alternatives and Tradeoffs *(Step 15)*

Only directions a strong senior engineer would put on the table, aimed at the weakest points found in Step 14: **how messages are sent**, and **the absence of host identity**.

### Directions compared

| | **P** original | **A** outbox, in-app | **D** provider batch | **B** host-driven batches | **C** verified host email |
|---|---|---|---|---|---|
| **How** | Send each message inside the host's request | Queue in the same transaction; a scheduled task in the application sends | Hand the provider one batch request; results reported back asynchronously | The host's open page requests one small batch at a time | Management link sent to the host's address; reissued there |
| **Correctness** | Send-after-commit is a rule to remember | **Send-after-commit is structural** | Depends on handling asynchronous results | Progress is data | Adds a replacement that must be one change |
| **Complexity** | Lowest | Moderate | **Low** | Moderate | Moderate |
| **Operational burden** | Falls on the host | Low — backlog visible | Low, plus an inbound callback endpoint (a new trust boundary) | None | None |
| **Scalability** | Fails at normal size | **Best** | Good, within provider batch limits | Only while the host keeps the page open | Enables per-host fairness |
| **Changeability** | Poor | **Best** — every message kind attaches here | Tied to one provider | Medium | High |
| **Continues without the host** | — | ✓ | ✓ | ✗ | — |
| **Can announce promotions** | ✗ | **✓** | ✗ | ✗ | — |
| **Rests on an unverified fact** | — | — | **Yes — no provider is chosen** (T4) | — | — |

**Chosen: A + C** (D8, D9).
- **A over B.** B's only advantage — no autonomous execution — rested on an over-broad reading of O1. With O1 corrected, A is better on every other axis and is the only direction that could announce promotions (which D3 then adopted).
- **A over D.** D is simpler *if* the provider supports it — which is not yet a fact.
- **C** is independent of the sending choice, and is the smallest change that makes Q4 and Q8 answerable.

### Considered, not developed

| Direction | Why a senior would consider it | Why set aside |
|---|---|---|
| Serialisable isolation with retry instead of the per-event lock | Equal correctness without explicit locking | Addresses none of the top risks; harder to verify by reading; retries thrash under a burst |
| Stored confirmed count with a database check constraint | Capacity enforced in one atomic increment | Every reply path must keep it in sync (INV-D4 drift), and promotion still needs a locked selection — two mechanisms instead of one |
| A scheduled job that locks events at start time | The obvious model of "closes at 18:00" | The in-step check is needed regardless (KD4) |
| Third-party sign-in for hosts | Stronger identity, no email round-trip | Excludes hosts without that provider; heavier than the problem needs |

### Tradeoffs accepted

| Chose | Over | Paid for with |
|---|---|---|
| A per-event lock | Optimistic retry | Contention within one busy event |
| Derived state | Stored state | Recomputation on every read; ordering depends on a precise timestamp rule |
| One current reply per guest | A reply history | No audit trail if a guest disputes an outcome |
| Capability links | Accounts | Links that can be forwarded (X9) and cannot be revoked except by replacement |
| One in-application scheduled task | No autonomous execution | A drain whose stall is visible only in the host's view (RO-7) |
| Releasing a place on "Maybe" (D2) | Holding it for the hesitant | Perceived unfairness to guests who are unsure |
| Last write wins between tabs (D6) | A version check | A guest's earlier intention can win |
| No capacity editing until Q2 | Shipping an obvious feature | Hosts cannot change capacity at all |
| Close is final, including declines (D11) | Allowing declines after close | After close, the count can only be too high |
| Guests without email outside the system (D12) | Host-entered replies | Part of the count stays manual |

---

## Rollout and Migration Notes *(Step 16)*

A greenfield feature on an empty scaffold: no previous version and no data to migrate. What matters instead is **what becomes permanent the moment the first email is sent.**

### Release sequence, with exit conditions

**Stage 0 — Scaffold cleanup**
- Delete the `wasender` block from `application.yml` (D1).
- Delete `MessageComposer.tsx`, `RecipientTable.tsx`, `ResultsTable.tsx`, `FileUpload.tsx`, `bulkSendApi.ts` and `types.ts` — empty files left from an unrelated bulk-messaging tool.
- Report the committed vendor key to the owner of `RamiY123/Xperience-Task-1-2026-04`. Deleting it here does not remove it from history.

**Stage 1 — Reply Engine, with no user interface.** Built first because every failure it can have is silent. *Stage 1 decision:* the status unit (close, cancel — U6) is built here as a service method with no endpoint, because X4 needs a real cancel taking the same per-event lock. It records cancellation notices (INV-B12) from the start; until Stage 2 creates invitations there is no one to notify. *Exits when:*
- Capacity 1, twenty simultaneous "Yes" → exactly one Confirmed, nineteen Waitlisted in admission order (X1).
- Two Confirmed guests leave at once with two waitlisted → each promoted exactly once, each with one notice (X2, INV-B10).
- A reply whose transaction begins before the start but acquires the lock after it → refused (RC-1).
- Replies submitted during a cancel → none recorded after it (X4).
- A waitlisted guest repeating "Yes" → same position (INV-B6).

**Stages 2 and 3 — Event Management and the Outbox Drain, released together.** They cannot be released separately: under D9 a host receives their management link *only* through the drain.
⚠️ **Gate: not released until the Q8 limit is chosen.** Otherwise the first release is an open mail relay. *Chosen before Stage 2: D14.* *Exits when:*
- `Dana@x.com` then `dana@x.com` → one guest (D5).
- A 600-address batch submitted twice → 600 guests.
- No invitation can be queued before the host has verified (INV-A6).
- A guest link at a host action, and a management link at a guest action → identical refusals.
- Drain paused → the backlog grows visibly; nothing is lost.
- One provider call hangs → it times out; the rest keep moving.
- Application killed mid-backlog → sending resumes; at most one duplicate.
- A failed invitation resent → the previous link stops working (KD13).

**Stage 4 — Promotion and cancellation notices** (D3, D10). *Exits when:*
- Promoted, then declined before the notice is sent → notice skipped.
- Event cancelled after a promotion, before its notice → notice skipped.
- A promotion notice is sent → the guest's original invitation link still works (INV-B13).
- An event with 400 invited guests is cancelled → 400 cancellation notices, paced; none carries a link; every existing link still opens and shows *cancelled*.

**Not shipped:** capacity editing, until Q2 is answered; the management-link recovery entry point (U9), until Q9 is answered. Stages 2–3 build no recovery request on the gate's open path — until Q9, a host who loses their management link cannot regain it (RO-5, RD-5 stay open).

### Fixed from the first email

| What | Why it cannot change later |
|---|---|
| The URL shape of guest and host links | Every sent message contains one, and they stay valid until replaced (A6) |
| How links are protected in storage | Changing it cannot convert existing links — only invalidate them |
| Email normalisation and uniqueness per event | `ddl-auto` creates constraints once; adding one later **fails if existing rows already violate it** |

### Flags

No flag mechanism exists; capacity editing is held back by not shipping it. **One switch belongs in the first release: pausing the drain without redeploying** — the immediate response to RD-3. Because queued messages simply wait, pausing loses nothing.

### Rollback

| Rollback | Result |
|---|---|
| Code, after the message records and host-verification fields exist | Safe — older code ignores them |
| **Any version that resolves links differently** | ❌ **Breaks every message already delivered.** Emails cannot be recalled. |
| A version predating KD13, with queued messages waiting | ❌ The older drain expects a link that was never stored |
| Past a promotion whose notice is still queued | Safe — skipped if no longer true (INV-B11) |

### Operationally sensitive

- **The worst first event is the normal one.** A 600-guest wedding (F15) from a sending identity with no reputation looks like spam. The drain's pace starts low; the first real events should be small.
- **The drain cannot tell a test from a wedding.** Real addresses used while verifying Stages 2–3 receive real email and spend the reputation every future host shares.
- **A duplicate after a restart carries a different link** (KD13). Only the newest works; the message text should say so.

---

## Pre-Review Weakness Check *(Step 18)*

The draft was read end to end, looking only for sections that were vague, assumption-heavy, structurally incomplete, or under-argued. Fifteen weaknesses (W1–W15) were found. This section records what happened to each, so a reviewer starts from what is **known** to be weak rather than rediscovering it.

### Found and fixed

| # | Weakness | Fix |
|---|---|---|
| **W1** | S5 generated a link for **every** message, so a promotion notice silently replaced the guest's working invitation link — a direct contradiction between D3 and KD13. | Only invitation and management-link messages generate links. Notices carry none (INV-B13). |
| **W2** | The Access Gate was defined as "a link resolves, or the request is refused" — which excluded U1 and U9, the two flows that have no link. | The gate has an explicit **open path** that permits only U1 and U9 and never reveals an existing event. |
| **W3** | The message state machine had no *in-progress* state, so crash recovery and claiming could not be expressed. | *sending* added; a message stuck in it past the send timeout returns to *queued*. Retry policy: D15. |
| **W6** | U2 did not say whether a Closed, Cancelled or started event can receive invitations. | Refused. An invitation exists only to allow a reply, and none is possible then. |
| **W10** | RO-5 was marked *closed* by D9, whose only entry point (Q9) is open. | Marked **partly closed**; RD-5 reworded the same way. |
| **W13** | F14 and F15 were labelled facts but come from one stakeholder. | Marked as stakeholder input, with the weight F15 carries stated. |
| **W14** | The lock rule silently assumed the start time is an absolute instant; Q5 was treated as a wording detail. | Stated explicitly; Q5 now names what depends on it — the correctness of G6 and INV-B7. |

### Found and decided by the problem owner

| # | Weakness | Decision |
|---|---|---|
| **W5** | Cancellation was silent — the reasoning that reversed D3 applied even more strongly here. | **D10** — guests are told. |
| **W7** | Close blocks declines, so after close the count can only drift upward. | **D11** — kept as the brief defines it; the cost is recorded, not hidden. |
| **W8** | One guest = one email address, so people without their own address could not be counted. | **D12** — outside the system; the host handles them by phone. G1's scope is narrowed to match. |

### Remaining — known, not resolved

| # | Weakness | Why it remains |
|---|---|---|
| **W4** | **No event field can be edited.** A wrong date or venue means cancel, recreate, and re-invite everyone. | Editing the start time moves the lock boundary, and editing capacity is Q2. Neither has been designed. |
| **W9** | **A verified address costs nothing to obtain.** Per-host limits keyed by address are weak against a determined abuser using fresh addresses. | No stronger identity fits N1 as it stands. |
| **W11** | **The Access Gate rate limit is named three times and defined nowhere** — no key, no window, no response when it is exceeded. | Not designed. |
| **W12** | **Pacing and the Q8 limit have no stated basis.** At minimum, the limit must exceed the normal 600-guest event (F15), and the response to a batch over the limit is undefined. | **Partly resolved:** D14 sets the limit (1,000 / 24 h, above 600) and the response (reject whole). Pacing still has no provider basis (D13). |
| **W15** | **KD13 was unconfirmed** — yet the Link record, S5, INV-A7, INV-B13, RC-9 and two rollback rows rest on it. | **Resolved before Stage 2:** KD13 confirmed by the problem owner. |

Open questions still standing: **Q2, Q6, Q9.**

### What a reviewer should push on first

1. ~~**W15 / KD13** — the most weight resting on the least settled decision.~~ *KD13 confirmed before Stage 2.*
2. **Q8 with W9 and W12** — the release gate now has a number (D14), but a control that a fresh address defeats.
3. **W4** — the absence of any edit path will surface in the first real use.
4. **D11** — whether "final" should really stop a guest from saying they can't come.
5. **Q9** — host recovery is designed but has no way in.

### Where the design stands

**Strongest:** the core inside the database — concurrency (X1–X4 under one per-event lock), derived rather than stored state, one writer per record, and invariants tied to explicit controls.

**Weakest:** the edges where the system meets the world — email that may never arrive (RD-1), a shared sender reputation with an undecided limit (RD-3, Q8), identity that stops at an unverifiable address (W9), and host recovery with no entry point (Q9).
