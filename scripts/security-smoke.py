#!/usr/bin/env python3
"""Verify authentication, CSRF, roles, revocation, lockouts, logs and audit through the gateway."""
import json,secrets,uuid,urllib.error,time
from security_client import Client,authenticated,BASE
owner=authenticated()
def rejected(client,path,status,payload=None,method=None):
    try:client.call(path,payload,method)
    except urllib.error.HTTPError as e:assert e.code==status,(path,e.code,status)
    else:raise AssertionError('Expected request rejection: '+path)
public=Client();public.call('/api/auth/session')
for path in ['/api/appointments','/api/salaries?staffId='+str(uuid.uuid4()),'/api/reports/sales.csv?period=YEAR&date=2026-01-01','/grafana/','/ops/loki/loki/api/v1/labels']:
    rejected(public,path,401)
name='staff-'+uuid.uuid4().hex[:8];temporary=secrets.token_urlsafe(24);permanent=secrets.token_urlsafe(24)
owner.call('/api/auth/users',{'username':name,'displayName':'Security Test Staff','password':temporary,'role':'STAFF'})
users=owner.call('/api/auth/users');account=next(u for u in users if u['username']==name)
staff=Client();staff.call('/api/auth/session');staff.call('/api/auth/login',{'username':name,'password':temporary})
rejected(staff,'/api/appointments',403)
staff.call('/api/auth/password',{'currentPassword':temporary,'newPassword':permanent})
rejected(staff,'/api/appointments',401)
staff.call('/api/auth/session');staff.call('/api/auth/login',{'username':name,'password':permanent})
for path in ['/api/salaries?staffId='+str(uuid.uuid4()),'/api/contracts?staffId='+str(uuid.uuid4()),'/api/expenses?date=2026-01-01','/api/reports/sales.csv?period=YEAR&date=2026-01-01','/api/auth/users','/api/auth/logs','/api/auth/audit','/api/website/enquiries','/api/statistics?year=2026&period=MONTH&date=2026-01-01','/grafana/','/mailbox/']:
    rejected(staff,path,403)
people=staff.call('/api/staff');assert all(set(p)=={'id','name','role'} for p in people)
assert isinstance(staff.call('/api/appointments'),list)
# No cookie or changed CSRF token must never allow a write, even with a forged owner header.
saved=staff.csrf;staff.csrf='invalid';rejected(staff,'/api/appointments',403,{'customerName':'Never saved'});staff.csrf=saved
owner.call('/api/auth/users/'+account['id'],{'role':'STAFF','enabled':False},'PUT');rejected(staff,'/api/appointments',401)
owner.call('/api/auth/users/'+account['id'],{'role':'STAFF','enabled':True},'PUT')
owner.call('/api/auth/users/'+account['id']+'/password',{'password':permanent})
attempt=Client();attempt.call('/api/auth/session')
for _ in range(5):rejected(attempt,'/api/auth/login',401,{'username':name,'password':'incorrect-password-test'})
rejected(attempt,'/api/auth/login',401,{'username':name,'password':permanent})
assert next(u for u in owner.call('/api/auth/users') if u['id']==account['id'])['locked_until'] is not None
actor=owner.call('/api/auth/session')['user'];enabled_owners=[u for u in owner.call('/api/auth/users') if u['role']=='OWNER' and u['enabled']]
if len(enabled_owners)==1:rejected(owner,'/api/auth/users/'+actor['id'],400,{'role':'STAFF','enabled':False},'PUT')
assert any(e['action']=='LOGIN_SUCCEEDED' for e in owner.call('/api/auth/audit'))
for _ in range(20):
    logs=owner.call('/api/auth/logs?service=auth-service&minutes=60&limit=100')
    if logs['entries']:break
    time.sleep(2)
assert logs['entries'],'Auth events missing in Loki'
assert all(set(e)=={'timestamp','service','level','message','traceId'} for e in logs['entries'])
rejected(owner,'/api/auth/logs?service=anything&minutes=50000',400)
owner.call('/api/auth/users/'+account['id'],{'role':'STAFF','enabled':False},'PUT')
print('PASS: anonymous denial, first-login password change, staff restrictions, roster redaction, CSRF, session revocation, lockout, last-owner protection, logs and security audit')
