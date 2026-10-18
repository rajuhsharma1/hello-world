#!/usr/bin/env python3
"""Exercise real website storage, attachments, email capture, publishing and analytics.
Use only on a disposable test deployment: creates sample enquiries and sales.
"""
import json,time,uuid,urllib.request,urllib.error
from security_client import authenticated,Client,BASE
owner=authenticated();public=Client()
def reject(fn,status):
 try:fn()
 except urllib.error.HTTPError as e:assert e.code==status,(e.code,status)
 else:raise AssertionError('Expected rejection')
def form(details,resume=None,origin=BASE):
 boundary='ark-'+uuid.uuid4().hex
 body=(f'--{boundary}\r\nContent-Disposition: form-data; name="details"\r\nContent-Type: application/json\r\n\r\n'+json.dumps(details)+'\r\n').encode()
 if resume is not None:body+=f'--{boundary}\r\nContent-Disposition: form-data; name="resume"; filename="cv.pdf"\r\nContent-Type: application/pdf\r\n\r\n'.encode()+resume+b'\r\n'
 body+=f'--{boundary}--\r\n'.encode()
 with public.opener.open(urllib.request.Request(BASE+'/api/public/enquiries',data=body,headers={'Content-Type':'multipart/form-data; boundary='+boundary,'Origin':origin,'X-Ark-Public':'1'}),timeout=30) as r:assert r.status==202;return json.load(r)
settings=owner.call('/api/website/settings');assert public.call('/api/public/settings')['recipient_email']==settings['recipient_email']
reject(lambda:public.call('/api/website/enquiries'),401)
reject(lambda:public.call('/api/statistics?year=2005&period=MONTH&date=2005-01-01'),401)
reject(lambda:public.opener.open(BASE+'/mailbox/'),401)
tag=uuid.uuid4().hex[:8]
def payload(kind):return {'requestId':str(uuid.uuid4()),'kind':kind,'name':'Website Test '+tag,'email':'applicant@example.test','phone':'+919876543219','subject':'Website smoke '+tag,'message':'<script>Unsafe text must stay text.</script>','answers':{},'consent':True,'website':''}
with owner.raw('/api/reports/profit-loss.pdf?date=2005-01-01') as r:resume=r.read()
career=payload('CAREER');career['answers']={'position':'Therapist','experience':'3 years','availability':'30 days','whyJoin':'Professional care','qualifications':'Test course'}
reference=form(career,resume)['reference'];assert reference==career['requestId']
complaint=payload('COMPLAINT');assert form(complaint)['reference']==complaint['requestId']
reject(lambda:form({**career,'requestId':str(uuid.uuid4())},b'MZ invalid executable'),400)
reject(lambda:form(payload('CONTACT'),origin='https://forged.example'),403)
reject(lambda:form({**payload('CONTACT'),'consent':False}),400)
with owner.raw('/api/website/enquiries/'+reference+'/resume') as r:assert r.read()==resume;assert r.headers['Content-Disposition'].startswith('attachment')
owner.call('/api/website/enquiries/'+complaint['requestId'],{'status':'IN_REVIEW'},'PUT')
for _ in range(25):
 entries=owner.call('/api/website/enquiries');created=[e for e in entries if e['id'] in (reference,complaint['requestId'])]
 if len(created)==2 and all(e['mail_state']=='SENT' for e in created):break
 time.sleep(2)
assert len(created)==2 and all(e['mail_state']=='SENT' for e in created),'Email capture did not complete'
assert next(e for e in created if e['id']==complaint['requestId'])['status']=='IN_REVIEW'
mail=owner.call('/mailbox/api/v1/messages');assert mail['total']>=2
post={'title':'Test journal '+tag,'excerpt':'Plain text introduction','body':'<img src=x onerror=alert(1)>','published':False}
id=owner.call('/api/website/blogs',post)['id'];assert all(p['id']!=id for p in public.call('/api/public/blogs'))
owner.call('/api/website/blogs/'+id,{**post,'published':True},'PUT');assert any(p['id']==id and p['body']==post['body'] for p in public.call('/api/public/blogs'))
owner.call('/api/website/blogs/'+id,post,'PUT')
staff=owner.call('/api/staff',{'name':'Statistics '+tag,'role':'Therapist'})
def sale(day,amount,phone):owner.call('/api/sales',{'requestId':str(uuid.uuid4()),'day':day,'customerName':'Stats Client','contactNumber':phone,'treatment':'Massage','staffId':staff['id'],'amount':amount,'paymentMethod':'UPI'})
for day,amount,phone in [('2004-01-03','10.00','+919000000001'),('2005-01-02','20.00','+919000000001'),('2005-01-03','30.00','+919000000001'),('2005-01-31','40.00','+919000000002'),('2005-02-01','50.00','+919000000003')]:sale(day,amount,phone)
def stats(period,date):return owner.call('/api/statistics?year=2005&period='+period+'&date='+date)
def person(s):return next(p for p in s['staff'] if p['id']==staff['id'])
s=stats('WEEK','2005-01-03');assert s['from']=='2005-01-03' and s['until']=='2005-01-10';assert person(s)['visits']==1 and float(person(s)['revenue'])==30
s=stats('MONTH','2005-01-05');p=person(s);assert p['visits']==3 and p['unique_clients']==2 and float(p['revenue'])==90
s=stats('YEAR','2005-06-01');assert person(s)['visits']==4 and float(person(s)['revenue'])==140;assert len(s['months'])==12;assert float(s['months'][0]['previous_revenue'])>=10
reject(lambda:owner.call('/api/statistics?year=1900&period=MONTH&date=2005-01-01'),400)
print('PASS: career PDF upload/download, complaints/status, consent/origin/type rejection, protected inbox, local email capture, draft/published blogs, monthly/YoY revenue and staff week/month/year boundaries')
