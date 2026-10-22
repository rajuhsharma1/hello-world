# Local development and recovery

Run from `ecal/`. Full Docker mode: `make up`, `make smoke`, `make down`. Host mode: `make down`, `make dev-db`, then `make dev-backend` and `make dev-frontend` in separate terminals. See the README for requirements and tests. These are mutually exclusive modes because both use backend/gateway port 8090.

| Symptom | Evidence | Recovery |
|---|---|---|
| Startup takes time | `docker compose logs --tail=80 api db` | Wait for DB health, migrations, then API health; `make up` waits for readiness. |
| Port already used | `docker compose ps`; check local services on 8090/5173/5544 | Stop the previous ECAL mode; do not stop the separate Ark stack. |
| Database password changed in `.env` but login fails | API logs show connection authentication failure | Restore the existing password or deliberately rotate the DB user; an environment edit does not rotate persisted credentials. |
| Maven/Node cannot download | Inspect failing hostname, proxy/CA and actual tool versions | Use a trusted corporate CA and supported Maven/npm configuration. Never disable TLS verification. |
| Testcontainers cannot find Docker | `docker info`, socket access, Docker Desktop status | Start Docker and grant normal socket access. Tests must fail rather than be silently skipped. |
| Stock runs out after many manual/E2E orders | GET `/api/catalog/products` | Use the local-only SQL replenishment below or create a fresh disposable lab. Do not confuse demo replenishment with a future admin feature. |
| A checkout response is lost | UI preserves exact pending key/payload | Use **Retry exact checkout** (wording may vary) without editing the request. Do not create a new key merely because the response timed out. |
| Last-unit checkout is rejected | `409 INSUFFICIENT_STOCK` | Refresh inventory; reduce quantity. There is no committed order/payment for this rolled-back attempt. |

To replenish **only the local lab** without losing history:

```sh
docker compose exec db psql -U ecal -d ecal -c 'update inventory_stock set available=20;'
```

This is an operator exercise, not an application endpoint. Do not run it during concurrent tests; the tests use their own disposable database and seed stock themselves.

Database durability check: place an order, record its ID, `make down`, `make up`, and GET `/api/orders/{id}` or reload history. Volume retention proves a container restart is not data loss; it is not a backup/restore or disaster-recovery proof. No tested backup RPO/RTO is claimed.

No real secrets or customer data are required. Default credentials are for an isolated loopback-only learning database. The API has a fixed shared demo buyer and no production identity or per-user authorization; do not expose it to a LAN/public internet. Only Nginx's loopback port is published in Docker mode; the API/database have no host bindings. Development publishes the database on loopback only.
