# ECAL buyer interface

This is the first UI vertical slice: product discovery → one-product checkout → persisted order outcome/history. It is a loopback-only learning lab with one shared demo buyer, no sign-in and no real payment provider. Identity and object-level access controls belong to a later milestone; do not expose this slice publicly.

## Run and verify

Use Node 22.x (22.22.2+), Node 24.x (24.15+), or Node 26+ (Node 24.19.0 was used for the initial build).

```sh
cd ecal/frontend
npm ci
npm run dev
```

The backend must run on `127.0.0.1:8090`. Vite serves the UI on `127.0.0.1:5173` and forwards `/api` requests to that backend. Override `ECAL_API_TARGET` for another local backend address. Both addresses bind to loopback by default. For full-system startup, use the ECAL root README and Compose configuration.

```sh
npm test                  # isolated React behavior tests; no database needed
npm run build             # strict TypeScript checking, then production assets
npm run test:e2e          # real browser + running frontend/backend/PostgreSQL
```

Install the E2E browser once with `npx playwright install chromium`, or set `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` to an existing Chromium executable. `ECAL_WEB_URL` overrides the E2E browser origin; the default is `http://127.0.0.1:5173`. E2E checks create real local orders and consume one unit of stock per approved checkout. They are for the disposable learning database.

## Responsibility and reasoning

| File | Responsibility and design reason |
|---|---|
| `src/App.tsx` | One buyer journey. Local Hooks own form/pending/result state; shared server reads stay explicit rather than introducing a global store before cross-page coordination exists. |
| `src/components/` | Stateless catalog, persisted outcome and history components: presentation receives data/callbacks and cannot mint command identities or mutate stock. |
| `src/api.ts` | Typed REST contracts, bounded fetch timeouts, known business errors and safe checkout acknowledgement validation. Decimal prices stay strings across the contract; only the backend decides authoritative monetary totals. |
| `src/styles.css` | Responsive layout and local CSS illustrations, with keyboard focus and reduced-motion support. No external font, image or analytics request is required. |
| `src/App.test.tsx` | Proves successful/declined outcomes and failure recovery rather than testing React internals. |
| `../tests/e2e/` | Proves the real browser sees persisted orders after reload and the mobile page remains usable. |

A dedicated global state library such as Redux Toolkit/RTK Query would help when multiple feature routes need cache invalidation, background refresh and shared mutation state. This slice has one coordinator and two small server reads, so the extra lifecycle abstraction would obscure the transaction lesson. Local state trades automatic cache deduplication for transparent code: after checkout, the catalog and order history are explicitly re-read.

Hash links navigate sections; no client router is needed for three sections on one page. The public REST order-by-ID operation still backs the history “View” action. Introduce a router when independent order URLs and separate buyer/admin screens exist.

## Failure behavior: a timeout is not a rollback

Before sending checkout, the UI freezes **both** a UUID idempotency key and the exact command. It saves those non-sensitive fields in `sessionStorage` so reloading the page does not mint a new business identity after a lost response. While unresolved, fields/product choice are locked and “Retry same checkout” resends the original request.

A known backend `400`/`404`/`409` problem response can clear that identity. A proxy timeout, unknown error page, server failure, dropped response, malformed JSON or a mismatched `2xx` acknowledgement cannot. A successful acknowledgement must match the expected product, quantity, simulated outcome and documented order shape. The UI then clears the pending identity and reloads durable state.

`sessionStorage` is not a database or a security boundary. It contains no token, password or payment credential, lasts only for the browser tab/session, and can be deleted by the user. Clearing it before resolving an uncertain operation loses that local recovery identity. In a multi-user authenticated phase, the server should expose scoped command-status lookup and a stronger recovery journey.

## Dependency evidence

All dependencies and the npm lockfile are pinned. The first validation used React 19.3.1, TypeScript 7.0.2, Vite 8.3.5, Vitest 4.1.12, React Testing Library 16.3.3 and jest-dom 7.0.1. Vitest 5.0.5 was evaluated but rejected because its assertion generic declarations conflicted with the current jest-dom declarations; Vitest 4.1.12 explicitly supports Vite 8 and passed strict typechecking without suppressing library checks.

## Learning checkpoint

- Why does a UUID idempotency key need an immutable payload too? Reusing a key with changed meaning would make replay unsafe; the backend rejects conflicting payloads.
- Why can a browser not infer payment failure from a request timeout? The database may already have committed before the response was lost.
- Why is the total on the checkout form only an estimate? Client data is editable; the authoritative price snapshot and exact decimal arithmetic belong to the backend transaction.
- Why not Redux or GraphQL now? Neither solves a demonstrated coordination/query problem in this single-page slice; evaluate them when the requirements change.

Exercise: use browser devtools to interrupt the checkout response after sending it, reload the tab, and retry the preserved request. Verify one order reference and one stock decrement. Then explain which part of the guarantee comes from the UI and which must come from PostgreSQL constraints/transactions.
