# ADR-012: ECS/EKS comparison belongs to a reviewed later cloud design

**Status:** Deferred — Phase 6, no resources provisioned

## Context

The current learning outcome runs locally. There is no approved production traffic, availability requirement, cloud budget or Kubernetes operating team.

## Decision — what and why

Keep Docker/Compose as the local execution boundary. For a small team's future container workload, evaluate ECS with managed PostgreSQL first; compare EKS only when Kubernetes ecosystem/team requirements justify its control-plane/operations work.

## Alternatives — why not now

EKS offers Kubernetes portability and platform extensibility but needs cluster/add-on/security expertise. EC2 is familiar but increases host maintenance. Lambda changes runtime/workload assumptions. None is automatically cheapest without region, traffic and staffing inputs.

## Consequences and trade-offs

This avoids cloud spend now and leaves multi-AZ, IAM, secrets, backups, ingress and rollout as explicit design work rather than claimed features. Local Compose is not a production HA platform.

## Failure behavior

No cloud recovery or autoscaling guarantee exists today. A future plan must account for failed AZs, database failover, insufficient capacity and secret rotation.

## Validation — how to prove it

Phase 6 needs a reviewed AWS HLD, resource/cost assumptions with current price evidence, least-privilege policy and recovery rehearsals where actually executed. A diagram or IaC plan is not a deployment test.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

Explicit authorization and a concrete deployment requirement/budget. No paid resources are deployed by this repository change.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
