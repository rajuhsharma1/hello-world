package com.spa.operations;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class WebsiteTests {

  @Test
  void staffWeeksCrossYearsAndMonthsUseCalendarBoundaries() {
    assertEquals(
      LocalDate.of(2024, 12, 30),
      StatisticsController.from("WEEK", LocalDate.of(2025, 1, 1))
    );
    assertEquals(
      LocalDate.of(2025, 1, 6),
      StatisticsController.until("WEEK", LocalDate.of(2024, 12, 30))
    );
    assertEquals(
      LocalDate.of(2024, 3, 1),
      StatisticsController.until(
        "MONTH",
        StatisticsController.from("MONTH", LocalDate.of(2024, 2, 29))
      )
    );
    assertThrows(ResponseStatusException.class, () ->
      StatisticsController.from("INVALID", LocalDate.of(2025, 1, 1))
    );
  }

  @Test
  void resumeRejectsRenamedExecutablesAndOversizedUploads() throws Exception {
    assertThrows(ResponseStatusException.class, () ->
      WebsiteService.resume(
        new MockMultipartFile(
          "resume",
          "fake.pdf",
          "application/pdf",
          "MZ executable content".getBytes()
        )
      )
    );
    assertThrows(ResponseStatusException.class, () ->
      WebsiteService.resume(
        new MockMultipartFile(
          "resume",
          "big.pdf",
          "application/pdf",
          new byte[5 * 1024 * 1024 + 1]
        )
      )
    );
    assertNull(WebsiteService.resume(null));
  }

  @Test
  void designedDocumentPreservesFinancialRowsAndRepeatedPageFurniture()
    throws Exception {
    byte[] bytes;
    try (var layout = new Documents.Layout("Compensation & salary structure")) {
      layout.section("Monthly earnings");
      layout.field("Basic salary", "INR 25,000.00");
      layout.field("Monthly gross", "INR 30,000.00");
      for (int i = 0; i < 80; i++) layout.paragraph(
        "Reviewed employment clause " +
          i +
          ". " +
          "Full term details. ".repeat(12)
      );
      layout.signature("Employee signature / date");
      bytes = layout.finish();
    }
    try (var pdf = Loader.loadPDF(bytes)) {
      var text = new PDFTextStripper().getText(pdf);
      assertTrue(pdf.getNumberOfPages() > 2);
      assertTrue(text.contains("Basic salary"));
      assertTrue(text.contains("INR 25,000.00"));
      assertTrue(text.contains("Reviewed employment clause 79"));
      assertTrue(text.contains("Employee signature / date"));
      assertTrue(text.contains("1 / " + pdf.getNumberOfPages()));
    }
  }
}
