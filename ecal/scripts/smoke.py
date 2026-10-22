#!/usr/bin/env python3
"""Small functional proof against a running lab; creates one synthetic approved order.
The exhaustive concurrency/rollback suite uses a disposable Testcontainers database instead.
"""
import json, os, urllib.request, urllib.error, uuid
BASE=os.environ.get('ECAL_BASE_URL','http://127.0.0.1:8090').rstrip('/')
def call(path,payload=None,key=None):
    headers={'Content-Type':'application/json'}
    if key:headers['Idempotency-Key']=key
    request=urllib.request.Request(BASE+path,data=None if payload is None else json.dumps(payload).encode(),headers=headers)
    with urllib.request.urlopen(request,timeout=20) as r:return r.status,json.load(r)
_,catalog=call('/api/catalog/products')
product=next((p for p in catalog if p['availableStock']>0),None)
if not product:raise RuntimeError('All demo stock exhausted; use a fresh disposable lab database or replenish through its SQL runbook.')
key=str(uuid.uuid4());payload={'productId':product['id'],'quantity':1,'paymentOutcome':'APPROVE'}
status,order=call('/api/orders',payload,key);assert status==201 and order['status']=='CONFIRMED'
status,replay=call('/api/orders',payload,key);assert status==200 and replay==order
assert call('/api/orders/'+order['id'])[1]==order
assert any(o['id']==order['id'] for o in call('/api/orders')[1])
try:call('/api/orders',{**payload,'quantity':2},key)
except urllib.error.HTTPError as e:assert e.code==409
else:raise AssertionError('Changed request under same idempotency key was accepted')
print('PASS: catalog, persisted checkout/status/history, exact replay and conflicting-key rejection')
