package com.spa.operations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Service
public class OperationsService {
 public record StaffRequest(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=100) String role) {}
 public record AttendanceRequest(@NotNull UUID staffId,@NotNull LocalDate day,@NotNull Attendance.Status status,LocalTime checkIn,LocalTime checkOut) {}
 public record SaleRequest(@NotNull UUID requestId,@NotNull LocalDate day,@NotBlank @Size(max=100) String customerName,
  @NotBlank @Pattern(regexp="[+]?[0-9]{7,15}") @Size(max=16) String contactNumber,
  @NotBlank @Size(max=100) String treatment,@NotNull UUID staffId,
  @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal amount,@NotNull Sale.PaymentMethod paymentMethod) {}
 public record AttendanceView(UUID id,UUID staffId,String staffName,LocalDate day,Attendance.Status status,LocalTime checkIn,LocalTime checkOut,Long minutesWorked) {}
 public record SaleView(UUID id,LocalDate day,String customerName,String contactNumber,String treatment,UUID staffId,String staffName,BigDecimal amount,Sale.PaymentMethod paymentMethod,Instant createdAt) {}
 public record SalesSummary(long clients,BigDecimal total,BigDecimal upi,BigDecimal cash,BigDecimal card) {}
 public record SalesPage(List<SaleView> entries,int page,boolean hasNext,SalesSummary summary) {}
 public record Settings(LocalDate today,String zoneId,String currency) {}
 private final StaffRepository staff; private final AttendanceRepository attendance; private final SaleRepository sales; private final JdbcTemplate jdbc; private final Clock clock;
 public OperationsService(StaffRepository s,AttendanceRepository a,SaleRepository r,JdbcTemplate j,Clock c) {staff=s;attendance=a;sales=r;jdbc=j;clock=c;}
 public Settings settings() {return new Settings(LocalDate.now(clock),clock.getZone().getId(),"INR");}
 private void validateDay(LocalDate day) {if(day.isAfter(LocalDate.now(clock))) throw bad("Choose today or a past business date.");}
 private Staff member(UUID id) {return staff.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Staff member not found."));}
 private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 public List<Staff> staff() {return staff.findAll(Sort.by("name"));}
 public Staff addStaff(StaffRequest r) {return staff.save(new Staff(r.name().trim(),r.role().trim()));}
 @Transactional(readOnly=true)
 public List<AttendanceView> attendance(LocalDate day) {return attendance.findByDayOrderByStaffNameAsc(day).stream().map(this::attendanceView).toList();}
 @Transactional
 public AttendanceView markAttendance(AttendanceRequest r) {
  validateDay(r.day());
  if(r.status()==Attendance.Status.PRESENT) {
   if(r.checkIn()==null) throw bad("Check-in is required for present staff.");
   if(r.checkOut()!=null && !r.checkOut().isAfter(r.checkIn())) throw bad("Check-out must be after check-in on the same day.");
  } else if(r.checkIn()!=null || r.checkOut()!=null) throw bad("Absent and leave records cannot have working times.");
  Staff person=member(r.staffId());
  Attendance a=attendance.findByStaffIdAndDay(r.staffId(),r.day()).orElseGet(()->new Attendance(person,r.day()));
  a.status=r.status();a.checkIn=r.checkIn();a.checkOut=r.checkOut();
  return attendanceView(attendance.saveAndFlush(a));
 }
 private AttendanceView attendanceView(Attendance a) {return new AttendanceView(a.id,a.staff.id,a.staff.name,a.day,a.status,a.checkIn,a.checkOut,a.checkOut==null?null:Duration.between(a.checkIn,a.checkOut).toMinutes());}
 private SaleView saleView(Sale a) {return new SaleView(a.id,a.day,a.customerName,a.contactNumber,a.treatment,a.staff.id,a.staff.name,a.amount,a.paymentMethod,a.createdAt);}
 @Transactional
 public SaleView recordSale(SaleRequest r) {
  validateDay(r.day());
  var existing=sales.findById(r.requestId());
  if(existing.isPresent()) {
   Sale s=existing.get();
   if(!s.day.equals(r.day()) || !s.customerName.equals(r.customerName().trim()) || !s.contactNumber.equals(r.contactNumber().trim()) || !s.treatment.equals(r.treatment().trim()) || !s.staff.id.equals(r.staffId()) || s.amount.compareTo(r.amount())!=0 || s.paymentMethod!=r.paymentMethod())
    throw new ResponseStatusException(HttpStatus.CONFLICT,"This request was already saved with different details.");
   return saleView(s);
  }
  Sale s=new Sale();s.id=r.requestId();s.day=r.day();s.customerName=r.customerName().trim();s.contactNumber=r.contactNumber().trim();s.treatment=r.treatment().trim();s.staff=member(r.staffId());s.amount=r.amount();s.paymentMethod=r.paymentMethod();s.createdAt=clock.instant();
  return saleView(sales.saveAndFlush(s));
 }
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public SalesPage sales(LocalDate day,int page) {
  if(page<0) throw bad("Page must be zero or greater.");
  var results=sales.findByDay(day,PageRequest.of(page,50,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
  SalesSummary summary=jdbc.queryForObject("select count(*) clients, coalesce(sum(amount),0) total, coalesce(sum(case when payment_method='UPI' then amount else 0 end),0) upi, coalesce(sum(case when payment_method='CASH' then amount else 0 end),0) cash, coalesce(sum(case when payment_method='CARD' then amount else 0 end),0) card from sale where day=?",(rs,n)->new SalesSummary(rs.getLong("clients"),rs.getBigDecimal("total"),rs.getBigDecimal("upi"),rs.getBigDecimal("cash"),rs.getBigDecimal("card")),day);
  return new SalesPage(results.stream().map(this::saleView).toList(),page,results.hasNext(),summary);
 }
}
