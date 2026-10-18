package com.spa.operations;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
@RestController
public class OperationsController {
 private final OperationsService service;
 public OperationsController(OperationsService s) {service=s;}
 @GetMapping("/api/operations/settings") public OperationsService.Settings settings() {return service.settings();}
 @GetMapping("/api/staff") public List<Staff> staff() {return service.staff();}
 @PostMapping("/api/staff") @ResponseStatus(HttpStatus.CREATED) public Staff add(@Valid @RequestBody OperationsService.StaffRequest r) {return service.addStaff(r);}
 @GetMapping("/api/attendance") public List<OperationsService.AttendanceView> attendance(@RequestParam LocalDate date) {return service.attendance(date);}
 @PutMapping("/api/attendance") public OperationsService.AttendanceView attendance(@Valid @RequestBody OperationsService.AttendanceRequest r) {return service.markAttendance(r);}
 @GetMapping("/api/sales") public OperationsService.SalesPage sales(@RequestParam LocalDate date,@RequestParam(defaultValue="0") int page) {return service.sales(date,page);}
 @PostMapping("/api/sales") @ResponseStatus(HttpStatus.CREATED) public OperationsService.SaleView sale(@Valid @RequestBody OperationsService.SaleRequest r) {return service.recordSale(r);}
 @ExceptionHandler(ResponseStatusException.class) public ProblemDetail problem(ResponseStatusException e) {return ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason());}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT)
 public ProblemDetail conflict() {return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"This record was changed by another request. Refresh and try again.");}
}
