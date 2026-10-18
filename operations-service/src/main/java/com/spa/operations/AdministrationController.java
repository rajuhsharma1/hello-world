package com.spa.operations;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AdministrationController {
    private final AdministrationService service;

    public AdministrationController(AdministrationService s) {
        service = s;
    }

    @PutMapping("/api/staff/{id}")
    public Staff staff(@PathVariable UUID id, @Valid @RequestBody AdministrationService.StaffDetails r) {
        return service.updateStaff(id, r);
    }

    @GetMapping("/api/contracts")
    public List<Map<String, Object>> contracts(@RequestParam UUID staffId) {
        return service.contracts(staffId);
    }

    @PostMapping("/api/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> contract(@Valid @RequestBody AdministrationService.ContractRequest r) {
        return service.contract(r);
    }

    @GetMapping("/api/salaries")
    public List<Map<String, Object>> salaries(@RequestParam UUID staffId) {
        return service.salaries(staffId);
    }

    @PostMapping("/api/salaries")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> salary(@Valid @RequestBody AdministrationService.SalaryRequest r) {
        return service.salary(r);
    }

    @GetMapping("/api/expenses")
    public Map<String, Object> expenses(@RequestParam LocalDate date, @RequestParam(defaultValue = "0") int page) {
        return service.expenses(date, page);
    }

    @PostMapping("/api/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> expense(@Valid @RequestBody AdministrationService.ExpenseRequest r) {
        return service.expense(r);
    }

    @GetMapping("/api/contacts")
    public List<Map<String, Object>> contacts() {
        return service.contacts();
    }

    @PostMapping("/api/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> contact(@Valid @RequestBody AdministrationService.ContactRequest r) {
        return service.contact(null, r);
    }

    @PutMapping("/api/contacts/{id}")
    public Map<String, Object> contact(@PathVariable UUID id, @Valid @RequestBody AdministrationService.ContactRequest r) {
        return service.contact(id, r);
    }

    @DeleteMapping("/api/contacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteContact(id);
    }

    @GetMapping("/api/reports/profit-loss")
    public Map<String, Object> profit(@RequestParam LocalDate date) {
        return service.profitLoss(date);
    }

    @GetMapping("/api/reports/sales.csv")
    public ResponseEntity<String> csv(@RequestParam String period, @RequestParam LocalDate date) {
        var p = AdministrationService.period(period, date);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ark-sales-" + p.label() + ".csv").contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8)).body(service.salesCsv(period, date));
    }

    private ResponseEntity<byte[]> pdf(byte[] data, String filename, boolean download) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=" + filename + ".pdf").body(data);
    }

    @GetMapping("/api/reports/profit-loss.pdf")
    public ResponseEntity<byte[]> profitPdf(@RequestParam LocalDate date, @RequestParam(defaultValue = "false") boolean download) {
        return pdf(service.profitPdf(date), "ark-profit-loss-" + date.withDayOfMonth(1), download);
    }

    @GetMapping("/api/contracts/{id}/pdf")
    public ResponseEntity<byte[]> contractPdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) {
        return pdf(service.contractPdf(id), "ark-contract-" + id, download);
    }

    @GetMapping("/api/contracts/{id}/pack.pdf")
    public ResponseEntity<byte[]> packPdf(@PathVariable UUID id, @RequestParam UUID salaryId, @RequestParam(defaultValue = "false") boolean download) {
        return pdf(service.employmentPack(id, salaryId), "ark-employment-pack-" + id, download);
    }

    @GetMapping("/api/salaries/{id}/pdf")
    public ResponseEntity<byte[]> salaryPdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) {
        return pdf(service.salaryPdf(id), "ark-salary-" + id, download);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> problem(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflict() {
        return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Record conflicts with an existing entry. Refresh and try again."));
    }
}
