#!/usr/bin/env python3
"""Disposable, warm-cache read-path experiment. Never connects to the application's database.
Requires Python 3.10+, Docker, Java 21 and an already packaged ECAL jar; stdlib only.
"""
import argparse
import concurrent.futures
import csv
import datetime
import http.client
import json
import math
import os
from pathlib import Path
import platform
import socket
import subprocess
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
INDEX = """create index order_buyer_status_recent on purchase_order
(buyer_id,status,created_at desc,id desc)
include(product_id,product_name,quantity,unit_price,total_amount,currency,payment_status)"""
QUERY = """select id,product_id,product_name,quantity,unit_price,total_amount,currency,status,payment_status,created_at
from purchase_order where buyer_id='00000000-0000-0000-0000-000000000001'
and status='CANCELLED' order by created_at desc,id desc limit 21"""


def command(*args, data=None):
    return subprocess.run(args, input=data, text=True, check=True, capture_output=True).stdout.strip()


def percentile(values, percent):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent / 100) - 1)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rows', type=int, default=200000)
    parser.add_argument('--requests', type=int, default=600)
    parser.add_argument('--warmup', type=int, default=100)
    parser.add_argument('--concurrency', type=int, default=8)
    parser.add_argument('--rounds', type=int, default=2)
    parser.add_argument('--output', type=Path, default=ROOT / 'tests/performance/results')
    parser.add_argument('--java', default=str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java')
    args = parser.parse_args()
    if not (20000 <= args.rows <= 2000000 and 100 <= args.requests <= 10000 and 1 <= args.concurrency <= 32 and 1 <= args.rounds <= 5 and 1 <= args.warmup <= 1000):
        parser.error('rows 20000–2000000; requests 100–10000; concurrency 1–32; rounds 1–5; warmup 1–1000')
    jar = ROOT / 'backend/modular-monolith/target/modular-monolith-0.1.0-SNAPSHOT.jar'
    if not jar.exists():
        parser.error('Package the backend first: mvn -f backend/modular-monolith/pom.xml -DskipTests package')
    args.output.mkdir(parents=True, exist_ok=True)
    name = 'ecal-benchmark-' + uuid.uuid4().hex[:12]
    process = None
    api_log = None
    report = {'timestampUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'rows': args.rows, 'requestsPerRun': args.requests, 'warmupPerRun': args.warmup,
              'concurrency': args.concurrency, 'rounds': args.rounds,
              'hardware': {'platform': platform.platform(), 'logicalCpus': os.cpu_count()},
              'java': subprocess.run([args.java, '-version'], capture_output=True, text=True).stderr.strip(),
              'gitCommit': command('git', '-C', str(ROOT), 'rev-parse', 'HEAD'),
              'gitDirty': bool(command('git', '-C', str(ROOT), 'status', '--porcelain')),
              'workload': 'Warm-cache GET /api/orders/page?status=CANCELLED&limit=20; one buyer; 0.1% cancelled; synthetic data',
              'runs': []}
    try:
        command('docker', 'run', '-d', '--name', name, '-p', '127.0.0.1::5432',
                '-e', 'POSTGRES_DB=ecal_benchmark', '-e', 'POSTGRES_USER=ecal_benchmark',
                '-e', 'POSTGRES_PASSWORD=disposable_benchmark_only', 'postgres:17.6')
        for _ in range(60):
            try:
                command('docker', 'exec', name, 'pg_isready', '-U', 'ecal_benchmark', '-d', 'ecal_benchmark')
                break
            except subprocess.CalledProcessError:
                time.sleep(0.5)
        else:
            raise RuntimeError('Disposable database did not start')
        db_port = command('docker', 'port', name, '5432/tcp').rsplit(':', 1)[1]

        def sql(statement):
            # Container name is generated here; all SQL is fixture code, never user-provided text.
            return command('docker', 'exec', '-i', name, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
                           '-U', 'ecal_benchmark', '-d', 'ecal_benchmark', data=statement)

        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            api_port = sock.getsockname()[1]
        env = os.environ.copy()
        env.update(ECAL_DB_URL=f'jdbc:postgresql://127.0.0.1:{db_port}/ecal_benchmark',
                   ECAL_DB_USER='ecal_benchmark', ECAL_DB_PASSWORD='disposable_benchmark_only',
                   ECAL_BIND_ADDRESS='127.0.0.1', SERVER_PORT=str(api_port))
        api_log = (args.output / 'api.log').open('w', encoding='utf-8')
        process = subprocess.Popen([args.java, '-Xms128m', '-Xmx256m', '-jar', str(jar)], env=env, stdout=api_log, stderr=subprocess.STDOUT)
        for _ in range(120):
            if process.poll() is not None:
                raise RuntimeError('API exited; see output/api.log')
            try:
                conn = http.client.HTTPConnection('127.0.0.1', api_port, timeout=2)
                conn.request('GET', '/actuator/health')
                response = conn.getresponse()
                response.read()
                conn.close()
                if response.status == 200:
                    break
            except OSError:
                pass
            time.sleep(0.5)
        else:
            raise RuntimeError('API health timed out')
        if sql('select current_database()') != 'ecal_benchmark':
            raise RuntimeError('Refusing to seed anything except the disposable benchmark database')
        print(f'Seeding {args.rows:,} synthetic orders in isolated {name}', flush=True)
        sql(f"""insert into purchase_order(id,buyer_id,idempotency_key,product_id,product_name,quantity,unit_price,total_amount,currency,requested_payment,payment_status,status,created_at)
select md5('order-'||g)::uuid,'00000000-0000-0000-0000-000000000001',md5('key-'||g)::uuid,
'10000000-0000-0000-0000-000000000001','Architecture field notebook',1,499,499,'INR',
case when g%1000=0 then 'DECLINE' else 'APPROVE' end,
case when g%1000=0 then 'DECLINED' else 'AUTHORIZED' end,
case when g%1000=0 then 'CANCELLED' else 'CONFIRMED' end,
'2026-01-01'::timestamptz + g*interval '1 second' from generate_series(1,{args.rows}) g;""")
        sql("insert into simulated_payment(order_id,amount,status) select id,total_amount,payment_status from purchase_order; insert into notification_record(order_id,message) select id,'Benchmark fixture' from purchase_order;")
        report['database'] = sql('select version()')
        local = threading.local()

        def one(_):
            if not hasattr(local, 'conn'):
                local.conn = http.client.HTTPConnection('127.0.0.1', api_port, timeout=15)
            start = time.perf_counter_ns()
            local.conn.request('GET', '/api/orders/page?status=CANCELLED&limit=20')
            response = local.conn.getresponse()
            body = response.read()
            elapsed = (time.perf_counter_ns()-start)/1e6
            if response.status != 200 or len(json.loads(body)['items']) != 20:
                raise RuntimeError(f'Unexpected response {response.status}; benchmark aborted')
            return elapsed

        def cpu():
            try:
                raw = command('docker', 'exec', name, 'cat', '/sys/fs/cgroup/cpu.stat')
                return int(dict(line.split() for line in raw.splitlines())['usage_usec'])
            except (subprocess.CalledProcessError, KeyError, ValueError):
                return None

        def stats():
            return json.loads(sql("select row_to_json(s) from (select blks_hit,blks_read,xact_commit,temp_bytes from pg_stat_database where datname='ecal_benchmark') s"))

        for round_no in range(1, args.rounds+1):
            for variant in (['baseline', 'indexed'] if round_no % 2 else ['indexed', 'baseline']):
                sql('drop index if exists order_buyer_status_recent;')
                if variant == 'indexed':
                    sql(INDEX)
                sql('vacuum (analyze) purchase_order;')
                plan = json.loads(sql('explain (analyze,buffers,format json) '+QUERY))
                (args.output / f'plan-{round_no}-{variant}.json').write_text(json.dumps(plan, indent=2)+'\n')
                index_bytes = int(sql("select pg_indexes_size('purchase_order')"))
                with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
                    list(pool.map(one, range(args.warmup)))
                    # PostgreSQL stats update asynchronously. Read immediately before the measured requests.
                    time.sleep(1.1)
                    before = stats()
                    cpu_before = cpu()
                    start = time.perf_counter()
                    latencies = list(pool.map(one, range(args.requests)))
                    elapsed = time.perf_counter()-start
                    cpu_after = cpu()
                # Allow server cumulative stats to flush; this wait is outside latency/throughput timing.
                time.sleep(1.1)
                after = stats()
                with (args.output/f'latencies-{round_no}-{variant}.csv').open('w', newline='') as out:
                    writer = csv.writer(out, lineterminator="\n")
                    writer.writerow(['request', 'latency_ms'])
                    writer.writerows(enumerate(latencies, 1))
                run = {'round': round_no, 'variant': variant, 'p50Ms': percentile(latencies,50),
                       'p95Ms': percentile(latencies,95), 'p99Ms': percentile(latencies,99),
                       'requestsPerSecond': args.requests/elapsed, 'elapsedSeconds': elapsed,
                       'errors': 0, 'allOrderIndexesBytes': index_bytes,
                       'dbCpuMilliseconds': (cpu_after-cpu_before)/1000 if cpu_after is not None and cpu_before is not None else None,
                       'dbStatsDelta': {key: after[key]-before[key] for key in before},
                       'explainExecutionMs': plan[0]['Execution Time'],
                       'plan': plan[0]['Plan']}
                report['runs'].append(run)
                print(f'Round {round_no} {variant}: p50={run["p50Ms"]:.2f}ms p95={run["p95Ms"]:.2f}ms p99={run["p99Ms"]:.2f}ms DB CPU={run["dbCpuMilliseconds"]}ms', flush=True)
        (args.output/'summary.json').write_text(json.dumps(report, indent=2)+'\n')
        print('Results: '+str(args.output/'summary.json'), flush=True)
    finally:
        if process is not None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        if api_log is not None:
            api_log.close()
        subprocess.run(['docker','rm','-f','-v',name], capture_output=True)


if __name__ == '__main__':
    main()
