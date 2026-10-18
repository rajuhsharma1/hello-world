#!/usr/bin/env python3
"""Validate HR records, PDF downloads, complete CSV exports and expense-based P&L."""
import csv, io, json, os, uuid, urllib.request, urllib.error
from security_client import authenticated
SECURITY=authenticated()
from decimal import Decimal
base=os.environ.get('SPA_BASE_URL','http://localhost:8080').rstrip('/')
def raw(path,payload=None,method=None):
    body=None if payload is None else json.dumps(payload).encode()
    return SECURITY.open(urllib.request.Request(base+path,data=body,method=method,headers={'Content-Type':'application/json'}),timeout=30)
def call(path,payload=None,method=None):
    with raw(path,payload,method) as r:return json.load(r,parse_float=Decimal)
def reject(path,payload,status,method=None):
    try:call(path,payload,method)
    except urllib.error.HTTPError as e:assert e.code==status,(e.code,status)
    else:raise AssertionError('Expected rejection')
def check_pdf(path):
    with raw(path) as r:
        assert r.headers['Content-Type'].startswith('application/pdf')
        assert r.read().startswith(b'%PDF-')
        assert r.headers['Content-Disposition'].startswith('inline')
    with raw(path+('&' if '?' in path else '?')+'download=true') as r:assert r.headers['Content-Disposition'].startswith('attachment')
tag=str(uuid.uuid4())[:8]
s=call('/api/staff',{'name':'HR Smoke '+tag,'role':'Stylist'})
updated=call('/api/staff/'+s['id'],{'name':'Updated HR '+tag,'role':'Senior stylist','contactNumber':'+919876543220','address':'Employee address, Punjab','joinedOn':'2003-01-01'},'PUT')
assert updated['id']==s['id'] and updated['role']=='Senior stylist'
contract={'staffId':s['id'],'employerName':'The Ark Salon & Spa','workplaceAddress':'Test registered workplace, Punjab','employeeAddress':'Test employee address, Punjab','employeeContactNumber':'+919876543220','registrationNumber':'TEST-'+tag,'jurisdiction':'Punjab, India','employmentType':'Full-time employee','startDate':'2003-01-01','endDate':None,'probationMonths':0,'noticeDays':30,'workingHours':'To be confirmed against current applicable law.','weeklyRest':'Weekly rest to be confirmed by local adviser.','leaveTerms':'Applicable legal entitlement after review.','remunerationTerms':'As per separately reviewed salary structure.','statutoryBenefits':'Applicability pending qualified adviser review.','terminationTerms':'Subject to mandatory rights and reviewed notice terms.','confidentialityTerms':'Reasonable workplace confidentiality subject to law.','additionalTerms':'Test draft only.'}
c=call('/api/contracts',contract);check_pdf('/api/contracts/'+c['id']+'/pdf')
assert call('/api/contracts?staffId='+s['id'])[0]['staff_name']==updated['name']
salary={'staffId':s['id'],'effectiveFrom':'2003-01-01','annualCtc':'360000.00','monthlyBasic':'20000.00','monthlyHra':'5000.00','monthlyAllowances':'5000.00','monthlyEmployerContributions':'0.00','monthlyEmployeeDeductions':'1000.00','annualBonus':'0.00','notes':'Test manual structure; no statutory-rate inference.'}
pay=call('/api/salaries',salary);check_pdf('/api/salaries/'+pay['id']+'/pdf')
check_pdf('/api/contracts/'+c['id']+'/pack.pdf?salaryId='+pay['id'])
reject('/api/salaries',{**salary,'annualCtc':'1.00'},400)
assert call('/api/salaries?staffId='+s['id'])[0]['annual_ctc']==Decimal('360000.00')
contact={'name':'Electrician '+tag,'service':'Electrician','phone':'+919876543221','secondaryPhone':'112','address':'Local service area','notes':'Test maintenance contact.'}
co=call('/api/contacts',contact);call('/api/contacts/'+co['id'],{**contact,'service':'Electrician / maintenance'},'PUT')
assert any(x['id']==co['id'] and x['secondary_phone']=='112' for x in call('/api/contacts'))
day='2003-04-16';month='/api/reports/profit-loss?date='+day
before=call(month)
ids=set()
for i in range(52):
    request_id=str(uuid.uuid4());ids.add(request_id)
    call('/api/sales',{'requestId':request_id,'day':day,'customerName':'=SUM(1,1)' if i==0 else 'Export '+tag,'contactNumber':'+919876543222','treatment':'Export test','staffId':s['id'],'amount':'10.10','paymentMethod':'UPI'})
outside=str(uuid.uuid4())
call('/api/sales',{'requestId':outside,'day':'2003-05-01','customerName':'Next month '+tag,'contactNumber':'+919876543222','treatment':'Boundary test','staffId':s['id'],'amount':'11.11','paymentMethod':'CASH'})
for category,amount in [('SALARY','200.00'),('SUPPLIES','50.00')]:
    expense={'requestId':str(uuid.uuid4()),'day':day,'category':category,'description':'Smoke '+tag,'amount':amount,'paymentMethod':'CASH'}
    call('/api/expenses',expense);call('/api/expenses',expense)
after=call(month)
assert after['revenue']-before['revenue']==Decimal('525.20')
assert after['expenses']-before['expenses']==Decimal('250.00')
assert after['profit']-before['profit']==Decimal('275.20')
with raw('/api/reports/profit-loss.pdf?date='+day+'&download=true') as r:
    assert r.read().startswith(b'%PDF-') and 'attachment' in r.headers['Content-Disposition']
for period in ['DAY','MONTH','YEAR']:
    with raw('/api/reports/sales.csv?period='+period+'&date='+day) as response:
        assert 'attachment' in response.headers['Content-Disposition']
        rows=list(csv.DictReader(io.StringIO(response.read().decode('utf-8-sig'))))
    exported={r['ID'] for r in rows}
    assert ids<=exported,'Export truncated to a visible page'
    assert (outside in exported)==(period=='YEAR'),'Wrong calendar boundary'
    formula=[r for r in rows if r['ID'] in ids and r['Client'].startswith("'=SUM")]
    assert len(formula)==1 and formula[0]['Contact'].startswith("'+91")
print('PASS: staff update, contract/salary PDFs, salary validation, contacts, daily expenses, P&L and complete day/month/year CSV exports')
