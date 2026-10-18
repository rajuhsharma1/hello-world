package com.spa.operations;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdministrationTests {
 @Test void csvEscapesQuotesNewlinesAndSpreadsheetFormulas() {
  assertEquals("\"'  =SUM(A1)\"",Documents.csvCell("  =SUM(A1)"));
  assertEquals("\"a,\"\"b\"\"\nnext\"",Documents.csvCell("a,\"b\"\nnext"));
  assertEquals("\"'@command\"",Documents.csvCell("@command"));
 }
 @Test void calendarPeriodsHandleLeapYearsAndYearBoundaries() {
  var feb=AdministrationService.period("MONTH",LocalDate.of(2024,2,29));assertEquals(LocalDate.of(2024,3,1),feb.until());assertEquals(LocalDate.of(2024,2,1),feb.from());
  var year=AdministrationService.period("YEAR",LocalDate.of(2025,12,31));assertEquals(LocalDate.of(2026,1,1),year.until());
  assertThrows(ResponseStatusException.class,()->AdministrationService.period("EVERYTHING",LocalDate.now()));
 }
 @Test void salaryChecksCtcAndDeductionsBeforeSaving() {
  var staff=mock(StaffRepository.class);var jdbc=mock(JdbcTemplate.class);Staff s=new Staff("Asha","Stylist");when(staff.findById(s.id)).thenReturn(Optional.of(s));
  var service=new AdministrationService(jdbc,staff,Clock.systemUTC(),new ObjectMapper());
  var request=new AdministrationService.SalaryRequest(s.id,LocalDate.now(),new BigDecimal("500000"),new BigDecimal("20000"),new BigDecimal("10000"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("1000"),BigDecimal.ZERO,"");
  assertEquals(new BigDecimal("360000"),AdministrationService.calculatedCtc(request));
  assertThrows(ResponseStatusException.class,()->service.salary(request));verifyNoInteractions(jdbc);
 }
 @Test void pdfContainsUnicodeAmountsAndPaginatesWithoutTruncating() throws Exception {
  var paragraphs=new ArrayList<String>();paragraphs.add("Amount ₹100.10");for(int i=0;i<120;i++) paragraphs.add("Contract term "+i+": "+"W".repeat(180));
  byte[] pdf=Documents.pdf("Test report",paragraphs);
  try(var doc=Loader.loadPDF(pdf)){assertTrue(doc.getNumberOfPages()>2);String text=new PDFTextStripper().getText(doc);assertTrue(text.contains("₹100.10"));assertTrue(text.contains("Contract term 119"));}
 }
}
