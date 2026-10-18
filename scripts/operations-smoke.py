#!/usr/bin/env python3
"""Exercise the real staff, attendance and sales database through the gateway."""
import json, os, uuid, urllib.request, urllib.error
from security_client import authenticated
SECURITY=authenticated()
from decimal import Decimal
base=os.environ.get('SPA_BASE_URL','http://localhost:8080').rstrip('/')
def call(path,payload=None,method=None):
    body=None if payload is None else json.dumps(payload).encode()
    with SECURITY.open(urllib.request.Request(base+path,data=body,method=method,headers={'Content-Type':'application/json'}),timeout=15) as response:
        return json.load(response,parse_float=Decimal)
def rejected(path,payload,status,method=None):
    try: call(path,payload,method)
    except urllib.error.HTTPError as e:
        assert e.code==status,(e.code,status)
    else: raise AssertionError('Invalid request accepted')
day=call('/api/operations/settings')['today']
person=call('/api/staff',{'name':'Register Smoke '+str(uuid.uuid4())[:8],'role':'Stylist'})
record={'staffId':person['id'],'day':day,'status':'PRESENT','checkIn':'09:15','checkOut':'17:45'}
a=call('/api/attendance',record,'PUT');assert a['minutesWorked']==510
record.update(status='ABSENT',checkIn=None,checkOut=None)
b=call('/api/attendance',record,'PUT');assert a['id']==b['id'] and b['minutesWorked'] is None
assert len([x for x in call('/api/attendance?date='+day) if x['staffId']==person['id']])==1
record.update(status='PRESENT',checkIn='18:00',checkOut='09:00');rejected('/api/attendance',record,400,'PUT')
before=call('/api/sales?date='+day)['summary']
for method,amount in [('UPI','100.10'),('CASH','200.20'),('CARD','300.30')]:
    sale={'requestId':str(uuid.uuid4()),'day':day,'customerName':'Register Client','contactNumber':'+919876543216','treatment':'Haircut','staffId':person['id'],'amount':amount,'paymentMethod':method}
    entry=call('/api/sales',sale);again=call('/api/sales',sale);assert entry['id']==again['id']
    sale['amount']='0';rejected('/api/sales',sale,400)
after=call('/api/sales?date='+day)['summary']
assert after['clients']==before['clients']+3
assert after['total']-before['total']==Decimal('600.60')
assert after['upi']-before['upi']==Decimal('100.10')
assert after['cash']-before['cash']==Decimal('200.20')
assert after['card']-before['card']==Decimal('300.30')
print('PASS: attendance upsert/hours, invalid times, daily payment totals, sale replay and invalid amounts')
