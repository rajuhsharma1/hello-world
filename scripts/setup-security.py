#!/usr/bin/env python3
"""Generate missing local security settings, preserving configured values; never print secrets."""
from pathlib import Path
import os,secrets,tempfile
root=Path(__file__).resolve().parent.parent
path=root/'.env'
lines=path.read_text().splitlines() if path.exists() else []
settings={}
for i,line in enumerate(lines):
    if '=' in line and not line.lstrip().startswith('#'):
        name,value=line.split('=',1);settings[name.strip()]=(i,value.strip().strip('"\''))
def ensure(name,value):
    if name in settings and settings[name][1]:return
    content=name+'='+value
    if name in settings:lines[settings[name][0]]=content
    else:lines.append(content)
for name in ['ARK_INTERNAL_KEY','ARK_SETUP_TOKEN','GRAFANA_PASSWORD']:ensure(name,secrets.token_urlsafe(48))
ensure('ARK_PUBLIC_ORIGIN','http://localhost:8080');ensure('ARK_COOKIE_SECURE','false')
with tempfile.NamedTemporaryFile('w',dir=root,delete=False) as out:
    out.write('\n'.join(lines)+'\n');temporary=out.name
os.chmod(temporary,0o600);os.replace(temporary,path)
print('Security settings saved in .env. No account or default password was created.')
print('Start Docker Compose; use ARK_SETUP_TOKEN from .env for one-time owner setup. Never share the token.')
