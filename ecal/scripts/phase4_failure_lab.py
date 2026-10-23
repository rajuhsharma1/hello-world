#!/usr/bin/env python3
"""Exercise live Phase 4 autonomous recovery; no business tables are reset or deleted.

Run with other clients idle. The durable receipt is written before the sole new POST.
If interrupted, --resume uses exactly that identity; never invent another order to recover it.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ['docker', 'compose', '-f', str(ROOT / 'compose.phase4.yaml')]
BASE = 'http://127.0.0.1:8095'
SHARED_DB = False


def compose(*args):
    subprocess.run(COMPOSE + list(args), cwd=ROOT, check=True)


def request(path, payload=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if key:
        headers['Idempotency-Key'] = key
    req = urllib.request.Request(BASE + path, data=None if payload is None else json.dumps(payload).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=20) as response:
        return response.status, json.load(response)


def scalar(owner, sql):
    result = subprocess.run(COMPOSE + ['exec', '-T', ('orders-db' if SHARED_DB else owner + '-db'), 'psql', '-U', owner, '-d', owner, '-Atc', sql],
                            cwd=ROOT, check=True, capture_output=True, text=True, encoding='utf-8')
    return result.stdout.strip()


def await_state(order_id, target, seconds=90):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        _, order = request('/api/v1/orders/' + order_id)
        if order['status'] == target:
            return order
        time.sleep(0.5)
    raise RuntimeError('Timed out waiting for ' + target + '; resume the saved receipt, do not create another identity.')


def save(path, receipt):
    path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resume', type=Path)
    parser.add_argument('--compact', action='store_true', help='Use the optional compact PostgreSQL topology')
    args = parser.parse_args()
    global SHARED_DB
    SHARED_DB = args.compact
    if args.resume:
        prior = json.loads(args.resume.read_text(encoding='utf-8'))
        SHARED_DB = bool(prior.get('compact', False))
    if SHARED_DB:
        COMPOSE.extend(['-f', str(ROOT / 'compose.phase4.compact.yaml')])
    if args.resume:
        path = args.resume.resolve()
        receipt = json.loads(path.read_text(encoding='utf-8'))
        uuid.UUID(receipt['key'])
        uuid.UUID(receipt['command']['productId'])
        compose('up', '-d', '--no-build', '--wait', 'payment', 'reporting')
    else:
        compose('up', '-d', '--no-build', '--wait', 'payment', 'reporting')
        _, products = request('/api/v1/catalog/products')
        product = next(p for p in products if p['availableStock'] > 0)
        receipt = {'key': str(uuid.uuid4()), 'command': {'productId': product['id'], 'quantity': 1, 'paymentOutcome': 'APPROVE'},
                   'stockBefore': product['availableStock'], 'compact': SHARED_DB}
        directory = ROOT / '.runtime'
        directory.mkdir(exist_ok=True)
        path = directory / ('phase4-recovery-' + receipt['key'] + '.json')
        save(path, receipt)
    print('Recovery receipt:', path, flush=True)
    print('Keep other clients idle until the stock assertion finishes.', flush=True)
    try:
        if not args.resume:
            compose('stop', 'payment', 'reporting')
        if 'orderId' not in receipt:
            status, order = request('/api/v1/orders', receipt['command'], receipt['key'])
            assert status in (200, 202), (status, order)
            receipt['orderId'] = str(uuid.UUID(order['id']))
            save(path, receipt)
        order_id = str(uuid.UUID(receipt['orderId']))
        if not args.resume:
            await_state(order_id, 'PAYMENT_UNKNOWN')
            assert scalar('payment', "select count(*) from payment where order_id='" + order_id + "'") == '0'
            print('Observed durable PAYMENT_UNKNOWN while payment and reporting were stopped.', flush=True)
        compose('up', '-d', '--no-build', '--wait', 'payment')
        await_state(order_id, 'CONFIRMED')
        print('CONFIRMED by background recovery, using GET only after acceptance.', flush=True)
        for owner, table, column in [('orders', 'purchase_order', 'id'), ('inventory', 'reservation', 'order_id'), ('payment', 'payment', 'order_id')]:
            assert scalar(owner, 'select count(*) from ' + table + ' where ' + column + "='" + order_id + "'") == '1'
        product_id = str(uuid.UUID(receipt['command']['productId']))
        assert int(scalar('inventory', "select available from stock where product_id='" + product_id + "'")) == receipt['stockBefore'] - 1
        compose('up', '-d', '--no-build', '--wait', 'reporting')
        end = time.monotonic() + 60
        while scalar('reporting', "select count(*) from order_projection where order_id='" + order_id + "' and status='CONFIRMED'") != '1':
            if time.monotonic() >= end:
                raise RuntimeError('Projection did not catch up; preserve the receipt and inspect consumer lag.')
            time.sleep(0.5)
        # The proxy must also recover its service address, not merely the private DB projection.
        end = time.monotonic() + 30
        while True:
            try:
                status, summary = request('/api/v1/reports/summary')
                if status == 200 and summary['consistency'] == 'EVENTUAL':
                    break
            except (urllib.error.HTTPError, urllib.error.URLError):
                pass
            if time.monotonic() >= end:
                raise RuntimeError('Projection recovered but public reporting route did not; inspect proxy DNS.')
            time.sleep(0.5)
        receipt['complete'] = True
        save(path, receipt)
        print('PASS: one order/reservation/payment, one stock decrement, reporting caught up through the public route.', flush=True)
    finally:
        # Restore dependencies even if an assertion fails; volumes and business data are preserved.
        compose('up', '-d', '--no-build', '--wait', 'payment', 'reporting')


if __name__ == '__main__':
    main()
