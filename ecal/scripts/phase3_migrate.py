#!/usr/bin/env python3
"""Offline Phase 2 -> Phase 3 backfill. Default apply mode only validates and prints a plan.
The CLI requires writers stopped. Low-level functions are exercised against isolated test databases.
No source row, application volume, credential or previously applied migration is deleted.
"""
import argparse
import csv
import datetime
from decimal import Decimal
import hashlib
import io
import json
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
ORDER_FIELDS = 'id,buyer_id,idempotency_key,product_id,product_name,quantity,unit_price::text,total_amount::text,currency,requested_payment,status,payment_status,created_at'
CATALOG_FIELDS = 'id,name,description,price::text,currency'
SEED_CATALOG = [
 {'id':'10000000-0000-0000-0000-000000000001','name':'Architecture field notebook','description':'Capture assumptions, diagrams and the decisions behind your next system.','price':'499.00','currency':'INR'},
 {'id':'10000000-0000-0000-0000-000000000002','name':'Focus desk lamp','description':'A warm, adjustable light for deep work and thoughtful design.','price':'2499.50','currency':'INR'},
 {'id':'10000000-0000-0000-0000-000000000003','name':'Everyday travel mug','description':'Keep the coffee warm while the architecture discussion gets interesting.','price':'899.00','currency':'INR'}
]



def run(args, data=None):
    result = subprocess.run(args, input=data, text=True, encoding="utf-8", capture_output=True)
    if result.returncode:
        # SQL can contain business data. Report a safe failure, not psql's echoed statement.
        raise RuntimeError('Docker/psql operation failed; inspect the selected database and migration prerequisites.')
    return result.stdout.strip()


class Node:
    def __init__(self, container, database, user):
        self.container, self.database, self.user = container, database, user

    def sql(self, statement):
        return run(['docker', 'exec', '-i', self.container, 'psql', '-XqAt', '-v', 'ON_ERROR_STOP=1',
                    '-U', self.user, '-d', self.database], "set timezone='UTC';\n"+statement)


def rows(table, columns, order):
    # Identifiers here are static source-code constants, never values interpolated from a snapshot.
    return f"coalesce((select jsonb_agg(to_jsonb(r) order by {order}) from (select {columns} from {table}) r),'[]'::jsonb)"


def object_query(parts):
    return 'select jsonb_build_object(' + ','.join("'"+key+"',"+value for key, value in parts.items()) + ')'


def digest(payload):
    return hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()).hexdigest()


def export_snapshot(source):
    statement = object_query({
        'catalog': rows('catalog_product', CATALOG_FIELDS, 'id'),
        'stock': rows('inventory_stock', 'product_id,available', 'product_id'),
        'orders': rows('purchase_order', ORDER_FIELDS, 'id'),
        'payments': rows('simulated_payment', 'order_id,amount::text,status', 'order_id'),
        'notifications': rows('notification_record', 'order_id,message', 'order_id')})
    # One statement provides one consistent read view; the CLI additionally requires source writers stopped.
    payload = json.loads(source.sql(statement))
    manifest = {'schemaVersion': 1, 'sha256': digest(payload),
                'exportedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'payload': payload}
    validate(manifest)
    return manifest


def validate(manifest):
    if manifest.get('schemaVersion') != 1 or digest(manifest['payload']) != manifest.get('sha256'):
        raise ValueError('Snapshot version/checksum mismatch.')
    data = manifest['payload']
    def unique(table, key):
        values = data[table]
        result = {value[key]: value for value in values}
        if len(result) != len(values):
            raise ValueError('Duplicate snapshot identity: '+table)
        for identity in result:
            uuid.UUID(identity)
        return result
    catalog, stock, orders, payments, notifications = (unique(*pair) for pair in
        [('catalog','id'), ('stock','product_id'), ('orders','id'), ('payments','order_id'), ('notifications','order_id')])
    if set(catalog) != set(stock) or set(orders) != set(payments) or set(orders) != set(notifications):
        raise ValueError('Snapshot ownership/FK coverage is incomplete.')
    if not {v['id'] for v in SEED_CATALOG}.issubset(catalog):
        raise ValueError('Source lacks expected seed identities; review a custom migration instead.')
    for product in catalog.values():
        if product['currency'] != 'INR' or not Decimal(product['price']).is_finite() or Decimal(product['price']) <= 0:
            raise ValueError('Invalid catalog money.')
    if any(type(value['available']) is not int or value['available'] < 0 for value in stock.values()):
        raise ValueError('Invalid available stock.')
    keys = set()
    for order in orders.values():
        uuid.UUID(order['buyer_id']); uuid.UUID(order['idempotency_key'])
        identity = (order['buyer_id'], order['idempotency_key'])
        if identity in keys:
            raise ValueError('Duplicate buyer/checkout key.')
        keys.add(identity)
        payment = payments[order['id']]
        expected = ('AUTHORIZED','APPROVE') if order['status'] == 'CONFIRMED' else ('DECLINED','DECLINE')
        if (order['status'] not in ('CONFIRMED','CANCELLED') or order['product_id'] not in catalog
            or order['currency'] != 'INR' or not 1 <= order['quantity'] <= 10
            or Decimal(order['unit_price']) <= 0
            or Decimal(order['unit_price'])*order['quantity'] != Decimal(order['total_amount'])
            or order['payment_status'] != expected[0] or order['requested_payment'] != expected[1]
            or payment['status'] != expected[0] or Decimal(payment['amount']) != Decimal(order['total_amount'])):
            raise ValueError('Only complete, consistent terminal Phase 2 orders may be migrated.')
    return data


def projections(manifest):
    data = validate(manifest)
    reservation = [{'order_id': o['id'], 'product_id': o['product_id'], 'quantity': o['quantity'],
                    'state': 'RESERVED' if o['status'] == 'CONFIRMED' else 'RELEASED'} for o in data['orders']]
    payment = [{'order_id': o['id'], 'amount': o['total_amount'], 'currency': o['currency'],
                'outcome': o['requested_payment'], 'status': o['payment_status']} for o in data['orders']]
    return {'orders': {'catalog': data['catalog'], 'orders': data['orders'], 'notifications': data['notifications']},
            'inventory': {'stock': data['stock'], 'reservations': reservation}, 'payment': {'payments': payment}}


def state(node, owner):
    queries = {
        'orders': {'catalog': rows('catalog_product', CATALOG_FIELDS, 'id'),
                   'orders': rows('purchase_order', ORDER_FIELDS, 'id'),
                   'notifications': rows('notification_record', 'order_id,message', 'order_id')},
        'inventory': {'stock': rows('stock', 'product_id,available', 'product_id'),
                      'reservations': rows('reservation', 'order_id,product_id,quantity,state', 'order_id')},
        'payment': {'payments': rows('payment', 'order_id,amount::text,currency,outcome,status', 'order_id')}}
    return json.loads(node.sql(object_query(queries[owner])))


def preflight(manifest, nodes):
    expected = projections(manifest)
    plan = {}
    for owner, node in nodes.items():
        receipt = node.sql('select snapshot_sha from migration_receipt where id=1')
        current = state(node, owner)
        if receipt:
            if receipt != manifest['sha256'] or current != expected[owner]:
                raise ValueError('Target has another snapshot or changed after migration; refusing '+owner)
            plan[owner] = 'already verified'
        else:
            business = {'orders': ('orders','notifications'), 'inventory': ('reservations',), 'payment': ('payments',)}[owner]
            if any(current[key] for key in business):
                raise ValueError('Target already owns business effects; refusing '+owner)
            # New targets must contain only the three untouched seed products/stock rows, not operator edits.
            seed_ids = {f'10000000-0000-0000-0000-{n:012d}' for n in range(1,4)}
            if owner == 'inventory' and (set(v['product_id'] for v in current['stock']) != seed_ids or any(v['available'] != 20 for v in current['stock'])):
                raise ValueError('Target stock is not a fresh seed; refusing overwrite.')
            if owner == 'orders':
                if current['catalog'] != SEED_CATALOG:
                    raise ValueError('Target catalog is not a fresh seed; refusing overwrite.')
            plan[owner] = 'copy and verify'
    return plan


# The JSON travels through COPY CSV, not SQL string interpolation: embedded quotes/newlines cannot become SQL.
TABLES = {
 'orders': [('catalog_product','catalog','id uuid,name varchar(120),description varchar(500),price numeric(12,2),currency char(3)','id,name,description,price,currency'),
            ('purchase_order','orders','id uuid,buyer_id uuid,idempotency_key uuid,product_id uuid,product_name varchar(120),quantity integer,unit_price numeric(12,2),total_amount numeric(14,2),currency char(3),requested_payment varchar(8),status varchar(20),payment_status varchar(12),created_at timestamptz', 'id,buyer_id,idempotency_key,product_id,product_name,quantity,unit_price,total_amount,currency,requested_payment,status,payment_status,created_at'),
            ('notification_record','notifications','order_id uuid,message varchar(240)','order_id,message')],
 'inventory': [('stock','stock','product_id uuid,available integer','product_id,available'),('reservation','reservations','order_id uuid,product_id uuid,quantity integer,state varchar(12)','order_id,product_id,quantity,state')],
 'payment': [('payment','payments','order_id uuid,amount numeric(14,2),currency char(3),outcome varchar(8),status varchar(12)','order_id,amount,currency,outcome,status')]
}


def apply_owner(manifest, nodes, owner):
    """Caller must exclude all writers. The CLI checks stopped apps; tests own every writer."""
    node = nodes[owner]
    projected = projections(manifest)[owner]
    payload = {'data': projected, 'sha256': manifest['sha256']}
    buffer = io.StringIO()
    csv.writer(buffer, lineterminator='\n').writerow([json.dumps(payload, ensure_ascii=False)])
    sql = 'begin;\ncreate temporary table migration_payload(value jsonb);\ncopy migration_payload from stdin with(format csv);\n'+buffer.getvalue()+'\\.\n'
    for table, key, definition, columns in TABLES[owner]:
        conflict = ('id' if table == 'catalog_product' else 'product_id') if table in ('catalog_product','stock') else None
        suffix = (' on conflict('+conflict+') do update set '+','.join(col+'=excluded.'+col for col in columns.split(',') if col != conflict)) if conflict else ''
        sql += f"insert into {table}({columns}) select {columns} from jsonb_to_recordset((select value->'data'->'{key}' from migration_payload)) as r({definition}) where true{suffix};\n"
    sql += "insert into migration_receipt(id,snapshot_sha) select 1,value->>'sha256' from migration_payload;\ncommit;\n"
    node.sql(sql)
    if state(node, owner) != projected:
        raise ValueError('Post-copy verification failed for '+owner+'. Keep both applications stopped.')


def apply_snapshot(manifest, nodes, execute=False):
    plan = preflight(manifest, nodes)  # Validate ALL targets before writing any owner.
    if execute:
        for owner in ('inventory','payment','orders'):
            if plan[owner] != 'already verified':
                apply_owner(manifest, nodes, owner)
        preflight(manifest, nodes)
    return plan


def container(compose, service, required=True):
    value = run(['docker','compose','-f',str(compose),'ps','-aq',service])
    if not value and required:
        raise ValueError('Missing container for '+service+'; initialize the stack/schema first.')
    if len(value.splitlines()) > 1:
        raise ValueError('Expected one local container for '+service)
    return value


def require_stopped(compose, services):
    for service in services:
        identity = container(compose, service, required=False)
        if identity and run(['docker','inspect','--format','{{.State.Running}}',identity]) == 'true':
            raise ValueError('Stop '+service+' in '+compose.name+' before migration. Database containers must stay running.')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='operation',required=True)
    export=sub.add_parser('export');export.add_argument('--out',type=Path,default=ROOT/'migration-exports/phase2-snapshot.json')
    apply=sub.add_parser('apply');apply.add_argument('snapshot',type=Path);apply.add_argument('--apply',action='store_true',help='Perform the already validated plan; default is dry-run')
    args=parser.parse_args()
    try:
        if args.operation=='export':
            require_stopped(ROOT/'compose.yaml',['web','api'])
            manifest=export_snapshot(Node(container(ROOT/'compose.yaml','db'),'ecal','ecal'))
            args.out.parent.mkdir(parents=True,exist_ok=True)
            with args.out.open('x',encoding='utf-8') as out:json.dump(manifest,out,indent=2,ensure_ascii=False)
            print('Read-only export verified: '+str(args.out))
        else:
            manifest=json.loads(args.snapshot.read_text(encoding='utf-8'));validate(manifest)
            require_stopped(ROOT/'compose.phase3.yaml',['web','api','inventory','payment'])
            nodes={owner:Node(container(ROOT/'compose.phase3.yaml',owner+'-db'),owner,owner) for owner in ('orders','inventory','payment')}
            plan=apply_snapshot(manifest,nodes,execute=args.apply)
            print(json.dumps({'mode':'applied and verified' if args.apply else 'DRY RUN: no writes','plan':plan},indent=2))
    except (RuntimeError,ValueError,KeyError,FileExistsError) as error:
        parser.exit(1,str(error)+'\n')


if __name__=='__main__':main()
