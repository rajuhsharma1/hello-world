#!/usr/bin/env python3
"""Live Compose drill: pause the private payment service, observe durable uncertainty, resume same key.
Creates one synthetic order, preserves its retry identity, and restores only Phase 3's payment service.
"""
import argparse
import json
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parents[1]
COMPOSE=['docker','compose','-f',str(ROOT/'compose.phase3.yaml')]
BASE='http://127.0.0.1:8091'


def run(args):
    result=subprocess.run(args,text=True,encoding="utf-8",capture_output=True)
    if result.returncode:raise RuntimeError('Docker operation failed: '+' '.join(args[:4]))
    return result.stdout.strip()


def http(path,body=None,key=None):
    headers={'Content-Type':'application/json'}
    if key:headers['Idempotency-Key']=key
    request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
    try:
        with urllib.request.urlopen(request,timeout=15) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as error:return error.code,json.load(error)


def sql(service,database,statement):
    # UUID values are parsed/canonicalized before reaching this static proof query.
    return run(COMPOSE+['exec','-T',service,'psql','-XqAt','-v','ON_ERROR_STOP=1','-U',database,'-d',database,'-c',statement])


def restore():run(COMPOSE+['up','-d','--no-build','--wait','--wait-timeout','90','payment'])


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--resume',type=Path);args=parser.parse_args()
    status,products=http('/api/v1/catalog/products')
    if status!=200:raise RuntimeError('Start the Phase 3 stack at 8091 first.')
    stopped=False
    if args.resume:
        path=args.resume;operation=json.loads(path.read_text());operation['key']=str(uuid.UUID(operation['key']));uuid.UUID(operation['command']['productId'])
    else:
        container=run(COMPOSE+['ps','-q','payment'])
        if not container or run(['docker','inspect','--format','{{.State.Health.Status}}',container])!='healthy':raise RuntimeError('Payment must be healthy before starting the failure drill.')
        product=next((p for p in products if p['availableStock']>0),None)
        if product is None:raise RuntimeError('No stock available; use another fresh Phase 3 lab or the inventory runbook.')
        operation={'key':str(uuid.uuid4()),'command':{'productId':product['id'],'quantity':1,'paymentOutcome':'APPROVE'},'baselineStock':product['availableStock']}
        path=ROOT/'.runtime'/('phase3-recovery-'+operation['key']+'.json');path.parent.mkdir(parents=True,exist_ok=True)
        with path.open('x') as out:json.dump(operation,out,indent=2)
    print('Retry identity saved: '+str(path),flush=True)
    try:
        if not args.resume:
            run(COMPOSE+['stop','payment']);stopped=True
            status,result=http('/api/v1/orders',operation['command'],operation['key'])
            assert status==503,(status,result)
            key=operation['key'];record=json.loads(sql('orders-db','orders',"select row_to_json(r) from (select id,status from purchase_order where idempotency_key='"+key+"'::uuid) r"))
            assert record['status']=='PAYMENT_UNKNOWN',record
            assert sql('payment-db','payment',"select count(*) from payment where order_id='"+record['id']+"'::uuid")=='0'
            print('PASS: payment unavailable -> PAYMENT_UNKNOWN; stock remains reserved, no false decline.',flush=True)
        restore();stopped=False
        status,result=http('/api/v1/orders',operation['command'],operation['key']);assert status in (200,201) and result['status']=='CONFIRMED',(status,result)
        status,replay=http('/api/v1/orders',operation['command'],operation['key']);assert status==200 and replay==result
        identity=str(uuid.UUID(result['id']))
        for service,database,table in [('orders-db','orders','purchase_order'),('inventory-db','inventory','reservation'),('payment-db','payment','payment')]:
            column='id' if table=='purchase_order' else 'order_id';assert sql(service,database,'select count(*) from '+table+' where '+column+"='"+identity+"'::uuid")=='1'
        if not args.resume:
            status,products=http('/api/v1/catalog/products');remaining=next(p['availableStock'] for p in products if p['id']==operation['command']['productId']);assert remaining==operation['baselineStock']-1
        operation['acknowledgedOrderId']=identity;path.write_text(json.dumps(operation,indent=2))
        print('PASS: same-key recovery and replay; one order, reservation and payment effect.',flush=True)
    finally:
        if stopped:restore()


if __name__=='__main__':main()
