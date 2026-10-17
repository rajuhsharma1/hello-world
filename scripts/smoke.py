#!/usr/bin/env python3
"""Run against the running Compose stack; validates persistence and Kafka delivery."""
import json, time, urllib.request, urllib.error, urllib.parse, uuid
TRACE_ID=uuid.uuid4().hex
from datetime import datetime, timedelta, timezone

def request(url, data=None):
    body=None if data is None else json.dumps(data).encode()
    with urllib.request.urlopen(urllib.request.Request(url,data=body,headers={"Content-Type":"application/json", **({"traceparent":f"00-{TRACE_ID}-0123456789abcdef-01"} if data is not None else {})}),timeout=10) as response:
        return json.load(response)
for port in (8080,8081):
    for attempt in range(90):
        try:
            assert request(f"http://localhost:{port}/actuator/health")["status"]=="UP"
            break
        except Exception:
            if attempt==89: raise
            time.sleep(2)
payload={"customerName":"Smoke Test","customerEmail":"smoke@example.com","treatment":"Massage","therapist":str(uuid.uuid4()),"startsAt":(datetime.now(timezone.utc)+timedelta(days=1)).isoformat()}
a=request("http://localhost:8080/api/appointments",payload)
assert request("http://localhost:8080/api/appointments/"+a["id"])["id"]==a["id"]
try:
    request("http://localhost:8080/api/appointments",payload)
    raise AssertionError("Duplicate booking was accepted")
except urllib.error.HTTPError as e:
    assert e.code==409
for attempt in range(60):
    notifications=request("http://localhost:8081/api/notifications")
    if any(n["appointmentId"]==a["id"] for n in notifications): break
    time.sleep(2)
else: raise AssertionError("Kafka notification not persisted")
print("PASS: booking persisted, duplicate rejected, Kafka notification persisted",a["id"])

for attempt in range(60):
    try:
        spans=request("http://localhost:9411/api/v2/trace/"+TRACE_ID)
        services={s.get("localEndpoint",{}).get("serviceName") for s in spans}
        if {"booking-service","notification-service"} <= services and any(s.get("kind")=="CONSUMER" and s.get("localEndpoint",{}).get("serviceName")=="notification-service" for s in spans): break
    except urllib.error.HTTPError as error:
        if error.code!=404: raise
    time.sleep(2)
else: raise AssertionError("Expected both services in distributed trace "+TRACE_ID)
query=urllib.parse.urlencode({"query":'{job="spa"} |= "'+a["id"]+'"',"limit":"100"})
for attempt in range(60):
    result=request("http://localhost:3100/loki/api/v1/query_range?"+query)
    if result["data"]["result"]: break
    time.sleep(2)
else: raise AssertionError("Application logs not found in Loki")
print("PASS: distributed trace contains both services; application logs available in Loki",TRACE_ID)
