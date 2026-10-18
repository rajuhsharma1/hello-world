package com.spa.operations;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OperationsTests {
 StaffRepository staff;AttendanceRepository attendance;SaleRepository sales;OperationsService service;Staff person;
 final LocalDate day=LocalDate.of(2026,10,18);
 @BeforeEach void setup() {
  staff=mock(StaffRepository.class);attendance=mock(AttendanceRepository.class);sales=mock(SaleRepository.class);
  person=new Staff("Asha","Stylist");when(staff.findById(person.id)).thenReturn(Optional.of(person));
  service=new OperationsService(staff,attendance,sales,mock(JdbcTemplate.class),Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"),ZoneId.of("Asia/Kolkata")));
 }
 @Test void attendanceRequiresValidTimesForStatus() {
  var reverse=new OperationsService.AttendanceRequest(person.id,day,Attendance.Status.PRESENT,LocalTime.of(18,0),LocalTime.of(9,0));
  assertEquals(400,assertThrows(ResponseStatusException.class,()->service.markAttendance(reverse)).getStatusCode().value());
  var absentWithTime=new OperationsService.AttendanceRequest(person.id,day,Attendance.Status.ABSENT,LocalTime.of(9,0),null);
  assertThrows(ResponseStatusException.class,()->service.markAttendance(absentWithTime));
  verifyNoInteractions(attendance);
 }
 @Test void attendanceUpdatePreservesIdentityAndComputesHours() {
  Attendance existing=new Attendance(person,day);when(attendance.findByStaffIdAndDay(person.id,day)).thenReturn(Optional.of(existing));
  when(attendance.saveAndFlush(any())).thenAnswer(invocation->invocation.getArgument(0));
  var result=service.markAttendance(new OperationsService.AttendanceRequest(person.id,day,Attendance.Status.PRESENT,LocalTime.of(9,15),LocalTime.of(17,45)));
  assertEquals(existing.id,result.id());assertEquals(510L,result.minutesWorked());
  var leave=service.markAttendance(new OperationsService.AttendanceRequest(person.id,day,Attendance.Status.LEAVE,null,null));
  assertEquals(existing.id,leave.id());assertNull(leave.minutesWorked());
 }
 OperationsService.SaleRequest request(UUID id,BigDecimal amount) {return new OperationsService.SaleRequest(id,day,"Client","+919876543210","Haircut",person.id,amount,Sale.PaymentMethod.UPI);}
 @Test void replayDoesNotCreateAnotherSaleAndChangedReplayIsRejected() {
  UUID id=UUID.randomUUID();Sale existing=new Sale();existing.id=id;existing.day=day;existing.customerName="Client";existing.contactNumber="+919876543210";existing.treatment="Haircut";existing.staff=person;existing.amount=new BigDecimal("1250.50");existing.paymentMethod=Sale.PaymentMethod.UPI;existing.createdAt=Instant.now();
  when(sales.findById(id)).thenReturn(Optional.of(existing));
  assertEquals(id,service.recordSale(request(id,new BigDecimal("1250.50"))).id());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.recordSale(request(id,new BigDecimal("1251.50")))).getStatusCode().value());
  verify(sales,never()).saveAndFlush(any());
 }
 @Test void rejectsFutureRegistersAndUnknownStaff() {
  var future=new OperationsService.AttendanceRequest(person.id,day.plusDays(1),Attendance.Status.LEAVE,null,null);
  assertThrows(ResponseStatusException.class,()->service.markAttendance(future));
  var missing=new OperationsService.AttendanceRequest(UUID.randomUUID(),day,Attendance.Status.LEAVE,null,null);
  assertEquals(404,assertThrows(ResponseStatusException.class,()->service.markAttendance(missing)).getStatusCode().value());
 }
 @Test void rejectsNegativeOrOverprecisePayments() {
  try(var validator=Validation.buildDefaultValidatorFactory()) {
   assertFalse(validator.getValidator().validate(request(UUID.randomUUID(),new BigDecimal("-1"))).isEmpty());
   assertFalse(validator.getValidator().validate(request(UUID.randomUUID(),new BigDecimal("1.001"))).isEmpty());
   assertTrue(validator.getValidator().validate(request(UUID.randomUUID(),new BigDecimal("0.01"))).isEmpty());
  }
 }
}
