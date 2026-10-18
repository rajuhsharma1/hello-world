#!/usr/bin/env python3
"""Explicit test-only owner bootstrap. Never run this helper for a real owner deployment."""
import json,os,secrets,time,urllib.error
from security_client import Client,ROOT
client=Client()
for attempt in range(90):
    try:status=client.call('/api/auth/session');break
    except Exception:
        if attempt==89:raise
        time.sleep(1)
path=ROOT/'.runtime/test-auth.json'
if status['needsSetup']:
    settings={}
    for line in (ROOT/'.env').read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key,value=line.split('=',1);settings[key.strip()]=value.strip().strip('"\'')
    credentials={'username':'test-owner','password':secrets.token_urlsafe(30)}
    client.call('/api/auth/setup',dict(credentials,displayName='Automated Test Owner',setupToken=settings['ARK_SETUP_TOKEN']))
    path.parent.mkdir(exist_ok=True);path.write_text(json.dumps(credentials));os.chmod(path,0o600)
    print('Disposable test owner created; credentials saved privately in .runtime/test-auth.json. Values were not printed.')
elif not path.exists():
    raise RuntimeError('An owner already exists. Configure test credentials explicitly; this helper will not take over an existing account.')
else:print('Using the existing private test credentials.')
