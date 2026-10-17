package com.spa.booking;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class BookingService {
 private final AppointmentRepository appointments; private final OutboxRepository outbox; private final ObjectMapper json; private final Tracer tracer;
 public BookingService(AppointmentRepository a,OutboxRepository o,ObjectMapper j,Tracer t) {appointments=a;outbox=o;json=j;tracer=t;}
 @Transactional
 public Appointment book(BookingRequest request) {
  var a=appointments.saveAndFlush(new Appointment(request));
  try {
   var span=tracer.currentSpan();
   String parent=span==null?null:"00-"+span.context().traceId()+"-"+span.context().spanId()+"-"+(Boolean.TRUE.equals(span.context().sampled())?"01":"00");
   outbox.save(new Outbox(a.id,json.writeValueAsString(a),parent));
  } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException(e);}
  return a;
 }
}
