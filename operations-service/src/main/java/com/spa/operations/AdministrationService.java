package com.spa.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdministrationService {

  public record StaffDetails(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 100) String role,
    @Pattern(regexp = "[+]?[0-9]{7,15}") String contactNumber,
    @Size(max = 1000) String address,
    LocalDate joinedOn
  ) {}

  public record ContractRequest(
    @NotNull UUID staffId,
    @NotBlank @Size(max = 200) String employerName,
    @NotBlank @Size(max = 1000) String workplaceAddress,
    @NotBlank @Size(max = 1000) String employeeAddress,
    @NotBlank @Pattern(regexp = "[+]?[0-9]{7,15}") String employeeContactNumber,
    @NotBlank @Size(max = 100) String registrationNumber,
    @NotBlank @Size(max = 100) String jurisdiction,
    @NotBlank @Size(max = 100) String employmentType,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @Min(0) @Max(24) int probationMonths,
    @Min(0) @Max(365) int noticeDays,
    @NotBlank @Size(max = 2000) String workingHours,
    @NotBlank @Size(max = 1000) String weeklyRest,
    @NotBlank @Size(max = 3000) String leaveTerms,
    @NotBlank @Size(max = 3000) String remunerationTerms,
    @NotBlank @Size(max = 3000) String statutoryBenefits,
    @NotBlank @Size(max = 3000) String terminationTerms,
    @NotBlank @Size(max = 2000) String confidentialityTerms,
    @Size(max = 4000) String additionalTerms
  ) {}

  public record SalaryRequest(
    @NotNull UUID staffId,
    @NotNull LocalDate effectiveFrom,
    @NotNull @DecimalMin("0.01") @Digits(
      integer = 12,
      fraction = 2
    ) BigDecimal annualCtc,
    @NotNull @DecimalMin("0.00") @Digits(
      integer = 10,
      fraction = 2
    ) BigDecimal monthlyBasic,
    @NotNull @DecimalMin("0.00") @Digits(
      integer = 10,
      fraction = 2
    ) BigDecimal monthlyHra,
    @NotNull @DecimalMin("0.00") @Digits(
      integer = 10,
      fraction = 2
    ) BigDecimal monthlyAllowances,
    @NotNull @DecimalMin("0.00") @Digits(
      integer = 10,
      fraction = 2
    ) BigDecimal monthlyEmployerContributions,
    @NotNull @DecimalMin("0.00") @Digits(
      integer = 10,
      fraction = 2
    ) BigDecimal monthlyEmployeeDeductions,
    @NotNull @DecimalMin("0.00") @Digits(
      integer = 10,
      fraction = 2
    ) BigDecimal annualBonus,
    @NotNull @Size(max = 4000) String notes
  ) {}

  public enum Category {
    RENT,
    SALARY,
    UTILITIES,
    SUPPLIES,
    MAINTENANCE,
    OTHER,
  }

  public record ExpenseRequest(
    @NotNull UUID requestId,
    @NotNull LocalDate day,
    @NotNull Category category,
    @NotBlank @Size(max = 500) String description,
    @NotNull @DecimalMin("0.01") @Digits(
      integer = 8,
      fraction = 2
    ) BigDecimal amount,
    @NotNull Sale.PaymentMethod paymentMethod
  ) {}

  public record ContactRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 100) String service,
    @NotBlank @Pattern(regexp = "[+]?[0-9]{3,15}") String phone,
    @Pattern(regexp = "[+]?[0-9]{3,15}") String secondaryPhone,
    @NotNull @Size(max = 1000) String address,
    @NotNull @Size(max = 2000) String notes
  ) {}

  public record Period(LocalDate from, LocalDate until, String label) {}

  private final JdbcTemplate db;
  private final StaffRepository staff;
  private final Clock clock;
  private final ObjectMapper json;

  public AdministrationService(
    JdbcTemplate d,
    StaffRepository s,
    Clock c,
    ObjectMapper j
  ) {
    db = d;
    staff = s;
    clock = c;
    json = j;
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Record not found."
    );
  }

  private Staff person(UUID id) {
    return staff.findById(id).orElseThrow(AdministrationService::missing);
  }

  @Transactional
  public Staff updateStaff(UUID id, StaffDetails r) {
    Staff s = person(id);
    s.name = r.name().trim();
    s.role = r.role().trim();
    s.contactNumber = r.contactNumber();
    s.address = r.address();
    s.joinedOn = r.joinedOn();
    return staff.saveAndFlush(s);
  }

  private Map<String, Object> one(String table, UUID id) {
    var rows = db.queryForList("select * from " + table + " where id=?", id);
    if (rows.isEmpty()) throw missing();
    return rows.getFirst();
  }

  public List<Map<String, Object>> contracts(UUID staffId) {
    return db.queryForList(
      "select id,staff_id,staff_name,staff_role,created_at from staff_contract where staff_id=? order by created_at desc,id desc",
      staffId
    );
  }

  @Transactional
  public Map<String, Object> contract(ContractRequest r) {
    Staff s = person(r.staffId());
    if (r.endDate() != null && r.endDate().isBefore(r.startDate())) throw bad(
      "End date must not precede the start date."
    );
    if (
      !r.jurisdiction().toLowerCase(Locale.ROOT).contains("punjab") ||
      !r.jurisdiction().toLowerCase(Locale.ROOT).contains("india")
    ) throw bad("This draft is configured for Punjab, India.");
    UUID id = UUID.randomUUID();
    try {
      db.update(
        "insert into staff_contract(id,staff_id,staff_name,staff_role,details,created_at) values (?,?,?,?,?::jsonb,?)",
        id,
        s.id,
        s.name,
        s.role,
        json.writeValueAsString(r),
        clock.instant().atOffset(ZoneOffset.UTC)
      );
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    return Map.of("id", id);
  }

  public List<Map<String, Object>> salaries(UUID staffId) {
    return db.queryForList(
      "select * from salary_structure where staff_id=? order by effective_from desc,created_at desc,id desc",
      staffId
    );
  }

  public static BigDecimal gross(SalaryRequest r) {
    return r.monthlyBasic().add(r.monthlyHra()).add(r.monthlyAllowances());
  }

  public static BigDecimal calculatedCtc(SalaryRequest r) {
    return gross(r)
      .add(r.monthlyEmployerContributions())
      .multiply(BigDecimal.valueOf(12))
      .add(r.annualBonus());
  }

  @Transactional
  public Map<String, Object> salary(SalaryRequest r) {
    Staff s = person(r.staffId());
    if (gross(r).signum() <= 0) throw bad(
      "Monthly gross salary must be positive."
    );
    if (calculatedCtc(r).compareTo(r.annualCtc()) != 0) throw bad(
      "Annual CTC must equal 12 × (basic + HRA + allowances + employer contributions) + annual bonus."
    );
    if (r.monthlyEmployeeDeductions().compareTo(gross(r)) > 0) throw bad(
      "Employee deductions cannot exceed monthly gross salary."
    );
    UUID id = UUID.randomUUID();
    db.update(
      "insert into salary_structure(id,staff_id,staff_name,effective_from,annual_ctc,monthly_basic,monthly_hra,monthly_allowances,monthly_employer_contributions,monthly_employee_deductions,annual_bonus,notes,created_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
      id,
      s.id,
      s.name,
      r.effectiveFrom(),
      r.annualCtc(),
      r.monthlyBasic(),
      r.monthlyHra(),
      r.monthlyAllowances(),
      r.monthlyEmployerContributions(),
      r.monthlyEmployeeDeductions(),
      r.annualBonus(),
      r.notes(),
      clock.instant().atOffset(ZoneOffset.UTC)
    );
    return Map.of("id", id);
  }

  @Transactional
  public Map<String, Object> expense(ExpenseRequest r) {
    if (r.day().isAfter(LocalDate.now(clock))) throw bad(
      "Expenses must use today or a past date."
    );
    var rows = db.queryForList(
      "select * from expense where id=?",
      r.requestId()
    );
    if (!rows.isEmpty()) {
      var old = rows.getFirst();
      if (
        !old.get("day").toString().equals(r.day().toString()) ||
        !old.get("category").equals(r.category().name()) ||
        !old.get("description").equals(r.description().trim()) ||
        ((BigDecimal) old.get("amount")).compareTo(r.amount()) != 0 ||
        !old.get("payment_method").equals(r.paymentMethod().name())
      ) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Request already saved with different expense details."
      );
      return Map.of("id", r.requestId());
    }
    db.update(
      "insert into expense(id,day,category,description,amount,payment_method,created_at) values (?,?,?,?,?,?,?)",
      r.requestId(),
      r.day(),
      r.category().name(),
      r.description().trim(),
      r.amount(),
      r.paymentMethod().name(),
      clock.instant().atOffset(ZoneOffset.UTC)
    );
    return Map.of("id", r.requestId());
  }

  @Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ
  )
  public Map<String, Object> expenses(LocalDate date, int page) {
    if (page < 0 || page > 1000000) throw bad("Invalid page.");
    return Map.of(
      "entries",
      db.queryForList(
        "select * from expense where day=? order by created_at desc,id desc limit 50 offset ?",
        date,
        page * 50
      ),
      "page",
      page,
      "summary",
      db.queryForMap(
        "select count(*) entries,coalesce(sum(amount),0) total from expense where day=?",
        date
      )
    );
  }

  public List<Map<String, Object>> contacts() {
    return db.queryForList(
      "select * from emergency_contact order by service,name,id"
    );
  }

  @Transactional
  public Map<String, Object> contact(UUID id, ContactRequest r) {
    if (id == null) id = UUID.randomUUID();
    else one("emergency_contact", id);
    db.update(
      "insert into emergency_contact(id,name,service,phone,secondary_phone,address,notes) values (?,?,?,?,?,?,?) on conflict(id) do update set name=excluded.name,service=excluded.service,phone=excluded.phone,secondary_phone=excluded.secondary_phone,address=excluded.address,notes=excluded.notes",
      id,
      r.name().trim(),
      r.service().trim(),
      r.phone(),
      r.secondaryPhone(),
      r.address(),
      r.notes()
    );
    return Map.of("id", id);
  }

  @Transactional
  public void deleteContact(UUID id) {
    if (
      db.update("delete from emergency_contact where id=?", id) == 0
    ) throw missing();
  }

  public static Period period(String type, LocalDate date) {
    return switch (type.toUpperCase(Locale.ROOT)) {
      case "DAY" -> new Period(date, date.plusDays(1), date.toString());
      case "MONTH" -> {
        var from = date.withDayOfMonth(1);
        yield new Period(
          from,
          from.plusMonths(1),
          YearMonth.from(date).toString()
        );
      }
      case "YEAR" -> {
        var from = date.withDayOfYear(1);
        yield new Period(
          from,
          from.plusYears(1),
          Integer.toString(date.getYear())
        );
      }
      default -> throw bad("Choose DAY, MONTH or YEAR.");
    };
  }

  public String salesCsv(String type, LocalDate date) {
    var p = period(type, date);
    StringBuilder csv = new StringBuilder("\uFEFF");
    csv.append("ID,Date,Client,Contact,Service,Staff,Amount INR,Payment\r\n");
    db.query(
      "select s.id,s.day,s.customer_name,s.contact_number,s.treatment,st.name,s.amount,s.payment_method from sale s join staff st on st.id=s.staff_id where day>=? and day<? order by day,created_at,s.id",
      (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
        for (int i = 1; i <= 8; i++) {
          if (i > 1) csv.append(',');
          csv.append(
            Documents.csvCell(i == 4 ? "'" + rs.getString(i) : rs.getObject(i))
          );
        }
        csv.append("\r\n");
      },
      p.from(),
      p.until()
    );
    return csv.toString();
  }

  @Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ
  )
  public Map<String, Object> profitLoss(LocalDate date) {
    var p = period("MONTH", date);
    var revenue = db.queryForMap(
      "select count(*) visits,coalesce(sum(amount),0) revenue from sale where day>=? and day<?",
      p.from(),
      p.until()
    );
    var expense = db.queryForMap(
      "select count(*) entries,coalesce(sum(amount),0) expenses from expense where day>=? and day<?",
      p.from(),
      p.until()
    );
    BigDecimal income = (BigDecimal) revenue.get("revenue"),
      cost = (BigDecimal) expense.get("expenses");
    return Map.of(
      "month",
      p.label(),
      "revenue",
      income,
      "expenses",
      cost,
      "profit",
      income.subtract(cost),
      "visits",
      revenue.get("visits"),
      "expenseEntries",
      expense.get("entries"),
      "categories",
      db.queryForList(
        "select category,sum(amount) amount from expense where day>=? and day<? group by category order by category",
        p.from(),
        p.until()
      )
    );
  }

  @Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ
  )
  public byte[] profitPdf(LocalDate date) {
    var p = profitLoss(date);
    List<String> lines = new ArrayList<>(
      List.of(
        "Monthly recorded operating profit / loss: " + p.get("month"),
        "Revenue collected (INR): " + p.get("revenue"),
        "Recorded expenses (INR): " + p.get("expenses"),
        "Net operating result (INR): " + p.get("profit"),
        "Attended visits: " + p.get("visits"),
        "Expense categories:"
      )
    );
    for (Object raw : (List<?>) p.get("categories")) {
      var row = (Map<?, ?>) raw;
      lines.add(row.get("category") + ": INR " + row.get("amount"));
    }
    lines.add(
      "Basis: recorded sales collections less recorded expenses for this calendar month in the salon business time zone. This is a management cash-based summary, not audited accounts or a statutory tax statement. Refunds, taxes, inventory, depreciation, outstanding amounts and accruals are not calculated. Salary structures are budgets, not expenses: record actual payroll once under SALARY to include it."
    );
    return Documents.pdf("Monthly profit / loss", lines);
  }

  public byte[] salaryPdf(UUID id) {
    var s = one("salary_structure", id);
    BigDecimal gross = ((BigDecimal) s.get("monthly_basic")).add(
      (BigDecimal) s.get("monthly_hra")
    ).add((BigDecimal) s.get("monthly_allowances"));
    try (var pdf = new Documents.Layout("Compensation & salary structure")) {
      pdf.section("01 / Employee & effective date");
      pdf.field("Staff", s.get("staff_name").toString());
      pdf.field("Effective from", s.get("effective_from").toString());
      pdf.field("Structure version", s.get("id").toString());
      pdf.section("02 / Monthly earnings · INR");
      pdf.field("Basic salary", money(s.get("monthly_basic")));
      pdf.field("House rent allowance", money(s.get("monthly_hra")));
      pdf.field("Other allowances", money(s.get("monthly_allowances")));
      pdf.field("Monthly gross", money(gross));
      pdf.section("03 / Deductions & take-home · INR");
      pdf.field(
        "Employee deductions",
        money(s.get("monthly_employee_deductions"))
      );
      pdf.field(
        "Indicative take-home",
        money(gross.subtract((BigDecimal) s.get("monthly_employee_deductions")))
      );
      pdf.section("04 / Employer cost & annual CTC · INR");
      pdf.field(
        "Employer contributions / month",
        money(s.get("monthly_employer_contributions"))
      );
      pdf.field("Annual bonus", money(s.get("annual_bonus")));
      pdf.field("Annual CTC", money(s.get("annual_ctc")));
      pdf.section("05 / Notes & acknowledgement");
      pdf.paragraph(s.get("notes").toString());
      pdf.paragraph(
        "CTC = 12 × (monthly gross + employer contributions) + annual bonus. Employer contributions are not deducted from take-home. This is a salary structure, not a payslip or proof of payment. No statutory rates, minimum wages, tax, EPF or ESI eligibility are inferred. Verify current Punjab, India requirements and employee applicability before use."
      );
      pdf.signature("Employer signature / date");
      pdf.signature("Staff acknowledgement / date");
      return pdf.finish();
    }
  }

  private static String money(Object value) {
    var format = java.text.NumberFormat.getNumberInstance(
      java.util.Locale.forLanguageTag("en-IN")
    );
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);
    return "INR " + format.format(value);
  }

  public byte[] employmentPack(UUID contractId, UUID salaryId) {
    if (
      !one("staff_contract", contractId)
        .get("staff_id")
        .equals(one("salary_structure", salaryId).get("staff_id"))
    ) throw bad("Contract and salary must belong to the same employee.");
    try (
      var contract = org.apache.pdfbox.Loader.loadPDF(contractPdf(contractId));
      var salary = org.apache.pdfbox.Loader.loadPDF(salaryPdf(salaryId));
      var combined = new org.apache.pdfbox.pdmodel.PDDocument();
      var output = new java.io.ByteArrayOutputStream()
    ) {
      var merger = new org.apache.pdfbox.multipdf.PDFMergerUtility();
      merger.appendDocument(combined, contract);
      merger.appendDocument(combined, salary);
      combined
        .getDocumentInformation()
        .setTitle("The Ark · Employment agreement and compensation");
      combined.save(output);
      return output.toByteArray();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Unable to create employment pack", e);
    }
  }

  public byte[] contractPdf(UUID id) {
    var c = one("staff_contract", id);
    try (var pdf = new Documents.Layout("Employment agreement · draft")) {
      pdf.callout(
        "DRAFT - FOR LOCAL LEGAL REVIEW; NOT A COMPLIANCE CERTIFICATE. Complete and review all terms before signing. Mandatory statutory rights prevail over any conflicting provision."
      );
      Map<String, Object> d;
      try {
        d = json.readValue(
          c.get("details").toString(),
          new com.fasterxml.jackson.core.type.TypeReference<>() {}
        );
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
      pdf.section("01 / Parties & place of employment");
      pdf.field("Employer", Objects.toString(d.get("employerName"), ""));
      pdf.field(
        "Registration number",
        Objects.toString(d.get("registrationNumber"), "")
      );
      pdf.field(
        "Workplace address",
        Objects.toString(d.get("workplaceAddress"), "")
      );
      pdf.field("Employee", c.get("staff_name").toString());
      pdf.field(
        "Employee address",
        Objects.toString(d.get("employeeAddress"), "")
      );
      pdf.field(
        "Contact number",
        Objects.toString(d.get("employeeContactNumber"), "")
      );
      pdf.field("Role", c.get("staff_role").toString());
      pdf.field("Draft version", id.toString());
      pdf.section("02 / Appointment & term");
      pdf.paragraph(
        "The employer proposes to engage the employee in the role stated above, subject to the reviewed terms below and applicable law."
      );
      pdf.field(
        "Employment type",
        Objects.toString(d.get("employmentType"), "")
      );
      pdf.field("Commencement date", Objects.toString(d.get("startDate"), ""));
      pdf.field(
        "End date",
        Objects.toString(d.get("endDate"), "Not specified")
      );
      pdf.field(
        "Probation (months)",
        Objects.toString(d.get("probationMonths"), "")
      );
      pdf.field(
        "Notice period (days)",
        Objects.toString(d.get("noticeDays"), "")
      );
      String[][] clauses = {
        { "03 / Working hours & weekly rest", "workingHours", "weeklyRest" },
        { "04 / Leave entitlement", "leaveTerms" },
        { "05 / Remuneration", "remunerationTerms" },
        { "06 / Statutory benefits", "statutoryBenefits" },
        { "07 / Termination & notice", "terminationTerms" },
        { "08 / Confidentiality", "confidentialityTerms" },
        { "09 / Additional agreed terms", "additionalTerms" },
      };
      for (String[] clause : clauses) {
        pdf.section(clause[0]);
        for (int i = 1; i < clause.length; i++) pdf.paragraph(
          Objects.toString(d.get(clause[i]), "Not specified")
        );
      }
      pdf.section("10 / Governing law & review");
      pdf.paragraph(
        "Jurisdiction: Punjab, India. Verify current applicable Punjab shops/commercial establishment legislation, rules, wage notifications and central labour/social-security requirements with a qualified local adviser. Hours, weekly rest, overtime, leave, notice, minimum wages, EPF/ESI and other entitlements depend on current law and establishment/employee applicability. This application does not determine legal compliance or supply statutory rates."
      );
      pdf.section("11 / Acceptance");
      pdf.paragraph(
        "By signing after review, the parties acknowledge the agreed terms and retain a copy of this document. Do not sign with incomplete or unreviewed statutory terms."
      );
      pdf.signature("Employer signature / date");
      pdf.signature("Employee signature / date");
      pdf.signature("Witness / date");
      return pdf.finish();
    }
  }
}
