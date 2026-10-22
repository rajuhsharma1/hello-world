# Quality attributes and initial NFR scorecard

Status: Phase 0 design hypotheses. Correctness checks can be proven by this slice's automated tests; availability, load latency and disaster-recovery figures below are **proposed study targets, not measurements, service guarantees, or employer requirements**. Current observed results belong in the execution/validation report after commands run.

## NFR scorecard

| ID | Attribute and stimulus | Proposed response/measure | Evidence now versus later |
| --- | --- | --- | --- |
| QA-01 | Correctness: simultaneous distinct orders compete for the last stock | Zero oversold units; stock ≥ 0; successful quantities ≤ initial stock | Real PostgreSQL concurrency test plus committed row/effect counts; a finite test proves the tested interleavings, not all possible system behavior |
| QA-02 | Idempotency: same key is submitted concurrently or after response loss | One committed order/payment/notification effect; same payload replays original result; changed command yields 409 | Concurrent/replay integration tests and UI uncertain-response test; not a claim of exactly-once distributed delivery |
| QA-03 | Consistency: simulation declines or the transaction fails | Decline commits CANCELLED with released stock; an exceptional rollback leaves no partial order/stock/local side effects | Domain and PostgreSQL integration tests. External payment ambiguity is outside this local transaction model |
| QA-04 | Performance: ordinary seeded-catalog checkout at a stated workload | **Hypothesis:** p95 < 300 ms at 10 checkout requests/second, up to 20 simultaneous clients, on a stated 4-vCPU/8-GB local test machine | Unmeasured. Phase 2 must record actual CPU/RAM/OS/JDK/DB, dataset, warmup, duration, success/error mix, p50/p95/p99 and raw samples under identical workload before comparing optimisations |
| QA-05 | Recovery: backend process restarts while PostgreSQL remains intact | Committed orders survive; interrupted uncommitted transaction rolls back; exact-key retry resolves an unknown HTTP result | Persistence/replay integration tests cover database behavior. A deliberate process-kill rehearsal and observed restart duration remain to be recorded; no RTO achieved claim |
| QA-06 | Availability: a future externally served learning deployment | **Design-only goal:** 99.5% monthly availability for valid API requests completing within a defined service budget; single-node local Compose cannot satisfy an HA claim | No SLO measurement or external deployment. Before adoption, define denominator, expected business rejections, maintenance window and measurement point; design/rehearse redundancy in Phase 6 |
| QA-07 | Backup/recovery: local database volume is lost | **Design-only targets:** daily logical backup, RPO ≤ 24 h and RTO ≤ 60 min for a small learning dataset, assuming a readable off-machine backup | No automatic backup job or restore rehearsal exists in the first slice. A named operator must implement and time backup/restore before these targets can be accepted |
| QA-08 | Security: an untrusted device/browser requests an order | Current containment is loopback binding, synthetic data, no external provider credentials; never claim buyer/admin isolation | Configuration review and negative input tests; authentication/object-level authorisation are a required future gate before untrusted exposure |
| QA-09 | Event durability: a broker is unavailable after business commit | Not applicable to this implementation: no broker/relay exists; local notification stub shares the order transaction | Phase 4 target: order and outbox commit atomically, relay resumes after failure, consumers deduplicate; no current claim of Kafka/event durability |
| QA-10 | Maintainability: a domain module changes | Domain logic stays independent of framework/persistence and module consumers use declared contracts | Architecture tests plus code review; test names and actual paths are indexed in the traceability matrix |
| QA-11 | Operability: a request is invalid or the database is unavailable | Validated problem-details response, no stack/secret leak; errors remain visible to the developer | HTTP integration tests and local logs; metrics, traces, alerts and distributed failure recovery are later work |
| QA-12 | Accessibility: keyboard or narrow-screen buyer completes checkout | Labelled controls, visible focus, announced errors/status, operable loading/retry flow | Frontend component and browser tests plus manual keyboard/mobile review; no WCAG conformance certification is claimed |

## Quality scenarios expressed as stimulus → response

1. **Stock contention:** during normal operation, two buyers request the last unit simultaneously → the database serialises conditional row updates; one order succeeds, the other receives a business conflict → assert final stock and effect counts in the real database.
2. **Lost response:** the server commits but the browser receives a transport failure → the browser preserves its submitted command/key, and replay returns the stored result → assert stable ID and one local payment effect.
3. **Decline:** a valid checkout chooses DECLINE → the local workflow records its outcome and releases stock before committing → compare inventory before/after and replay the decline.
4. **Database failure:** a checkout cannot finish its local transaction → no partial accepted order is exposed → assert rollback in an integration test; restore DB availability and retry the same command rather than inventing an order status.
5. **Growth:** order history exceeds a response window → the API still returns at most 50 records → verify the bound. Cursor pagination is an explicit next design exercise, not an implicit guarantee of unlimited history.
6. **Architectural evolution:** inventory becomes a remote service → the single local transaction no longer spans all effects → supersede the local transaction ADR and implement timeout/reconciliation/Saga tests before asserting equivalent guarantees.

## Cost and capacity assumptions

Local development uses Docker, one PostgreSQL instance, one Java process and one frontend; there is no paid infrastructure provisioned by this delivery. Existing machine/cloud fees, energy and storage still have costs, so “local” is not a claim of zero total cost. No cloud prices or production capacity figures are quoted.

Before proposing a deployment, collect region, active users, orders/second and peaks, product/order counts, retention, required availability, support hours, data residency, backup size and team operating skills. Estimate compute, relational storage/I/O, backups, network, logs and operator time separately; attach a dated provider estimate rather than inventing a monthly total.

## Review questions and evidence discipline

- Which invariant is a business requirement, and which number is merely our learning hypothesis?
- What happens to p95 if inventory contention rises while average latency remains low?
- Can a 50-row history test establish a throughput SLO? No: functional correctness and performance evidence answer different questions.
- What survives process restart, host loss, and backup loss respectively?
- Which safety claims depend on PostgreSQL, local simulation and a single database transaction, and would be invalid after extraction?

Record hardware, commands, timestamps, outcomes and limitations when each experiment runs. A test not executed is pending evidence, even if its code exists.
