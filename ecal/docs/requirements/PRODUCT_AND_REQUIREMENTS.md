# Product discovery and requirements

Status: Phase 0 working draft for review, not a record of stakeholder approval. Scope: the first small Phase 1 slice.

## Source boundary and product vision

The user's request is to build a runnable Phase 0/first Phase 1 architecture learning application and explain its engineering decisions. The supplied master prompt and interview blueprint inform the exercise. Their fictional commerce product, proposed tools, example NFRs and later milestones are learning proposals; they are not an employer's confirmed business requirements, a request to implement all phases now, or permission to deploy paid infrastructure.

**Vision:** Enterprise Commerce Architecture Lab (ECAL) lets a learner complete a believable checkout and then explain—with code and test evidence—why its inventory, order state and retry behavior are correct. The teaching objective is to move from framework knowledge to decisions grounded in business invariants, failure modes and operational cost.

Success in this slice means a buyer can browse seeded products, place a single-product order, and retrieve its persisted outcome after reloading the UI. The engineer can reproduce conflicting/concurrent requests and explain the database's role in correctness.

## Personas and their present access

| Persona | Goal | This slice | Later capability |
| --- | --- | --- | --- |
| Demo buyer | Understand a purchase and its outcome | Browse, choose quantity, select a visibly simulated payment result, view persisted history/status | Authenticated personal history, richer cart |
| Operations/support | Explain what happened to an order | Learner inspects the same demo data and database; no privileged admin API is claimed | Authorised inventory/order support views |
| Developer/on-call | Reproduce a failure safely | Automated tests, local restart and documented failure exercises | Metrics, tracing, replay/reconciliation tools |
| Solution architect | Defend a design to a customer | Requirements, HLD/LLD, ADRs, proof and explicit open risks | Extraction, cloud design, cost/DR review |

There are no independently authenticated buyer/admin identities yet. The backend selects a fixed demo buyer; a caller does not choose another buyer by sending an ID. This makes the limitation clear but does not provide real access control.

## Assumptions and questions

| ID | Working assumption | Why it keeps the slice coherent | Revisit question |
| --- | --- | --- | --- |
| A-01 | Learning data only; no real PII, payment credentials or money movement | Allows a safe local simulation | Which real identities, data retention and regulatory duties would apply to an actual product? |
| A-02 | One product per order, quantity 1–10, INR prices | Exposes transactional/concurrency decisions without multi-line cart allocation | Must checkout reserve a multi-item cart atomically? |
| A-03 | Catalog owns the current price; an order records its own price snapshot | Browser input cannot set the charged amount; history remains stable | Are promotions, taxes, shipping or currency conversion required? |
| A-04 | Payment is a local deterministic APPROVE/DECLINE simulator | Its persisted result participates in the same local transaction | When a real provider is introduced, what idempotency and reconciliation contracts does it offer? |
| A-05 | A local notification stub persists a simulated notification; no email is sent | Notification intent can be inspected without adding an external failure boundary | Which notifications need durable external delivery and what consent applies? |
| A-06 | One PostgreSQL database, one backend process, a local frontend, loopback access | A local ACID transaction is enough for the current invariants | What measured scale or team/deployment need would justify extraction? |
| A-07 | History returns the latest 50 demo orders | Bounds a first UI/API response | What history volume and pagination semantics do support users require? |
| A-08 | Invalid/insufficient-stock requests do not claim a durable idempotency outcome | A rolled-back request has no committed business effect | Should a future API durably remember rejected requests as well? |

These assumptions are review questions, not blocking approval gates for the authorised prototype. Changing one requires updating its affected ADR, API and tests.

## Business requirements (BRD)

| ID | Requirement | Business reason | Acceptance boundary |
| --- | --- | --- | --- |
| BR-01 | A learner can complete and explain one persisted buyer journey | A runnable slice provides stronger interview evidence than a diagram-only portfolio | Browse → submit → display → reload/read outcome through REST/UI |
| BR-02 | The system must not promise stock it does not have | Overselling creates a business obligation unsupported by inventory | Parallel successful reservations cannot consume more than available stock |
| BR-03 | Retrying one checkout must not create another purchase/payment effect | Network response loss should not multiply business effects | Same idempotency key and payload replay the committed order |
| BR-04 | Declined simulated payment must not retain stock | A declined purchase must not block later buyers | Persist CANCELLED/DECLINED and restore the reserved quantity atomically |
| BR-05 | Every substantial design must expose its rationale, alternatives, failure behavior and proof | Architect interviews require judgment rather than technology lists | Trace requirements to ADRs, code boundaries, tests and learning exercises |
| BR-06 | Keep local operation reproducible and protect the existing Ark project | The new learning lab should be independently runnable and reviewable | ECAL lives under `ecal/`, uses its own configuration/data, and needs no paid deployment |

## Functional requirements (FRD)

| ID | Contract | Acceptance criterion |
| --- | --- | --- |
| FR-01 | `GET /api/catalog/products` lists seeded products | Return product ID, name, description, decimal-string price, INR currency and current available stock; reads do not reserve stock |
| FR-02 | `POST /api/orders` accepts a product ID, quantity and APPROVE/DECLINE outcome | Require a UUID `Idempotency-Key`; reject malformed input and quantity outside 1–10 with problem details |
| FR-03 | Price is resolved by the server | Ignore/reject unsupported client pricing fields according to the API contract; accepted order amount equals the server product price × quantity using decimal arithmetic |
| FR-04 | New successful processing returns a persisted final outcome | HTTP 201 with order DTO; `GET /api/orders/{id}` and reload show the same order ID, product snapshot, amount and final state |
| FR-05 | Safe committed replay | Same key plus same product/quantity/payment outcome returns HTTP 200 with the original order; no new stock, payment or notification effect; a later catalog-price change does not rewrite that order |
| FR-06 | Conflicting replay is rejected | Same key with a different command returns HTTP 409; the original order and effects remain unchanged |
| FR-07 | Stock reservation is conditional and atomic | At most the available quantity is reserved under concurrent transactions; remaining stock never becomes negative |
| FR-08 | Insufficient stock has no partial committed effects | Return HTTP 409 and roll back the pending order, stock change and local side effects; a later retry is allowed to attempt again because no order committed |
| FR-09 | Only valid lifecycle transitions occur | Domain permits PENDING → RESERVED → CONFIRMED or RESERVED → CANCELLED; illegal transitions fail without changing an accepted outcome |
| FR-10 | A simulated decline releases its reservation | Commit one CANCELLED order, one DECLINED simulated payment result and its notification stub; stock is unchanged from before that checkout |
| FR-11 | Demo history is bounded and consistent with persisted orders | `GET /api/orders` returns latest 50 fixed-demo-buyer orders; unknown order IDs return 404; the UI handles loading, empty and error states |
| FR-12 | Errors are understandable without leaking internals | Use RFC 9457-style problem details including stable error code; SQL/stack traces and credentials do not become response bodies |
| FR-13 | A lost HTTP response can be retried without changing the command | UI retains the submitted key and payload on uncertain transport/server failure; a deliberate new purchase receives a new key |
| FR-14 | Notification is explicitly simulated | Persist the local stub with the order transaction; no SMTP, email, Kafka consumer or asynchronous delivery is implied |

## User stories and executable acceptance examples

### US-01 — Browse and buy (BR-01, FR-01–04)

As the demo buyer, I want to see products and their current prices before placing an order.

- Given seeded stock is available, when I submit an APPROVE order for quantity 2, then a CONFIRMED/AUTHORIZED order is returned and is readable after reload.
- The total is computed from the server price; the frontend labels payment as simulated.
- Backend persistence integration and browser journey tests are the intended proof.

### US-02 — Retry after uncertainty (BR-03, FR-05–06, FR-13)

As a buyer whose response was lost, I want to retry the same checkout without buying twice.

- Given a committed order, when its exact command is sent again with the same key, then the original ID/result returns with 200 and effect counts remain one.
- When the key is reused with changed quantity or payment outcome, then 409 is returned and no stored effect changes.
- A UI retry retains both key and payload; editing a new checkout cannot silently mutate the pending retry command.

### US-03 — Last-stock contention (BR-02, FR-07–08)

As a buyer, I want an order confirmation to mean stock was actually available.

- Given one remaining unit and simultaneous distinct-key requests for one unit, exactly one can commit a CONFIRMED order; others receive insufficient-stock conflicts.
- Stock is zero, not negative; unsuccessful attempts have no payment or notification rows.
- A real PostgreSQL integration test coordinates concurrent requests rather than simulating contention with a mock.

### US-04 — Declined payment (BR-04, FR-09–10, FR-14)

As a learner, I want a deterministic declined payment so I can explain inventory recovery.

- Given sufficient stock, when DECLINE is selected, then the final order is CANCELLED and the simulated result is DECLINED.
- Stock returns to its original level, and reusing that key does not repeat reservation/release or another payment result.
- The release is inside one local transaction. It is not a distributed Saga compensation.

### US-05 — Inspect saved history (BR-01, FR-11–12)

As a learner, I want to reopen the app and retrieve the actual persisted outcome.

- Reload shows saved demo orders; selecting an order shows its stored product name, quantity, amount and state.
- Unknown ID yields 404 problem details; a failed API response yields a useful UI error state.
- The UI does not claim personal account isolation because authentication is deferred.

### US-06 — Explain and reproduce (BR-05–06)

As an architecture candidate, I want startup commands and failure exercises tied to the implementation.

- A documented clean local startup runs independently of Ark.
- Tests demonstrate the invariants above; architecture documents distinguish implemented local behavior from future distributed designs.
- Any unrun performance, availability or disaster-recovery target is labelled unmeasured rather than reported as achieved.

## Explicit exclusions from this delivery

Multi-item carts, real payments, external notifications, stock administration, authentication/RBAC, other-user authorisation, Kafka, transactional outbox, Saga coordination, asynchronous projections, Redis, GraphQL and cloud deployment are later work. The first phase provides module boundaries and a truthful baseline for those experiments, not empty services that suggest the experiments already exist.
