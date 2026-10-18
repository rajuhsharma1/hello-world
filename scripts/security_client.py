"""Cookie/CSRF-aware client for explicitly configured automated test credentials."""
import http.cookiejar,json,os,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
BASE=os.environ.get('SPA_BASE_URL','http://localhost:8080').rstrip('/')
class Client:
    def __init__(self):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=''
    def raw(self,path,payload=None,method=None):
        method=method or ('POST' if payload is not None else 'GET')
        headers={'Content-Type':'application/json','Origin':BASE}
        if method not in ('GET','HEAD','OPTIONS'):headers['X-CSRF-Token']=self.csrf
        body=None if payload is None else json.dumps(payload).encode()
        return self.opener.open(urllib.request.Request(BASE+path,data=body,method=method,headers=headers),timeout=30)
    def call(self,path,payload=None,method=None):
        with self.raw(path,payload,method) as response:
            if response.status==204:return None
            result=json.load(response)
            if isinstance(result,dict) and result.get('csrfToken'):self.csrf=result['csrfToken']
            return result
    def open(self,request,timeout=30):
        if isinstance(request,str):request=urllib.request.Request(request)
        request.add_header('Origin',BASE)
        if request.get_method() not in ('GET','HEAD','OPTIONS'):request.add_header('X-CSRF-Token',self.csrf)
        return self.opener.open(request,timeout=timeout)
def credentials():
    if os.environ.get('ARK_TEST_USERNAME') and os.environ.get('ARK_TEST_PASSWORD'):
        return {'username':os.environ['ARK_TEST_USERNAME'],'password':os.environ['ARK_TEST_PASSWORD']}
    path=ROOT/'.runtime/test-auth.json'
    if not path.exists():raise RuntimeError('Set ARK_TEST_USERNAME/ARK_TEST_PASSWORD for tests, or run scripts/bootstrap-test-user.py on a disposable test environment.')
    return json.loads(path.read_text())
def authenticated():
    client=Client();client.call('/api/auth/session');client.call('/api/auth/login',credentials());return client
