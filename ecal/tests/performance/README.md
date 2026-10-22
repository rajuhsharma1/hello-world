# Reproduce the Phase 2 query experiment

This stdlib-only Python harness owns a fresh PostgreSQL 17.6 container and separate Java process. It never connects to the running application's database or changes its volume. It drops/recreates only the tested index in its disposable database. Normal exit/error cleanup stops its own process and removes its own anonymous-volume container. After a forced kill, inspect `docker ps -a --filter name=ecal-benchmark-` and remove only the corresponding experiment container, never the ECAL application volume.

Requirements: Docker running, Python 3.10+, Java 21, Maven 3.9+. From `ecal/` (PowerShell works without Make):

```sh
mvn -f backend/modular-monolith/pom.xml -DskipTests package
python tests/performance/benchmark.py
```

Use `python3` if that is your interpreter name. Set JAVA_HOME or pass `--java /path/to/java`. Linux/macOS GNU Make users can run `make benchmark`. Defaults: 200000 orders, 0.1% cancellations, 100 warmups, 600 measured HTTP requests, eight concurrent clients, two rounds in reversed order. A shorter exploratory run is `python tests/performance/benchmark.py --rows 20000 --requests 100 --rounds 1`. That is a different workload; do not compare it to the checked-in report.

Output goes to ignored `results/`: actual nearest-rank p50/p95/p99, throughput, raw latency CSVs, EXPLAIN ANALYZE BUFFERS JSON, database CPU cgroup deltas when available, approximate cumulative DB-stat deltas and API logs. Capture artifacts before a later run overwrites them, or pass `--output some-other-directory`. No latency/errors are invented when a run fails: failure aborts rather than producing a success summary.

The baseline and optimized variants use the **same query and endpoint**, dataset, JVM, host, concurrency and warmup. Baseline omits only the new status index; optimized restores it. Index build and VACUUM are outside request timing. This is a warm-cache selective read experiment, not a simulation of all production traffic. The harness does not measure checkout write overhead, CPU saturation, cold-cache behavior, or the Phase 0 end-to-end capacity goals. Shared-host interference and JIT still affect results. PostgreSQL statistics flush asynchronously, so blks/xact deltas are approximate diagnostics, not exact per-request counts. CPU is container aggregate sampled through `cpu.stat`, not isolated SQL CPU. Windows hosts may not expose that Linux cgroup metric; missing data stays null.

See [REPORT](REPORT.md) for actual observations and [observed raw artifacts](observed-2026-10-22/summary.json). Repeat on your own hardware before making a capacity decision.
