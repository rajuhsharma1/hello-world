package com.spa.booking;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
@RestController
@RequestMapping("/api/appointments")
public class BookingController {
 private final BookingService service; private final AppointmentRepository repository;
 public BookingController(BookingService s,AppointmentRepository r) {service=s;repository=r;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public Appointment create(@Valid @RequestBody BookingRequest r) {return service.book(r);}
 @GetMapping public java.util.List<Appointment> list(@RequestParam(defaultValue="0") int page) {if(page<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return repository.findAll(PageRequest.of(page,50,Sort.by("startsAt"))).getContent();}
 @GetMapping("/{id}") public Appointment get(@PathVariable UUID id) {return repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
 @ResponseStatus(HttpStatus.CONFLICT) public ProblemDetail conflict() {return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Therapist already has an appointment at this start time.");}
}
