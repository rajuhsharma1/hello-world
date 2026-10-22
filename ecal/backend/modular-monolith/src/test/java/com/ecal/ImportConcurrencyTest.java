package com.ecal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecal.catalog.internal.importing.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Controlled blocking proves admission and worker bounds without timing-dependent sleep assertions. */
class ImportConcurrencyTest {

  @Test
  void infrastructureFailureDoesNotClaimAConfirmedRollback() {
    var writer = mock(ImportBatchWriter.class);
    when(writer.write(anyList())).thenThrow(
      new org.springframework.dao.DataAccessResourceFailureException(
        "Connection lost around commit"
      )
    );
    var service = new CatalogImportService(writer);
    try {
      String csv =
        "productId,name,description,price,currency,stock\n20000000-0000-0000-0000-000000000001,Name,Description,1.00,INR,1\n";
      var result = service.run(
        new MockMultipartFile(
          "file",
          "test.csv",
          "text/csv",
          csv.getBytes(StandardCharsets.UTF_8)
        )
      );
      assertEquals(1, result.failedBatches());
      assertEquals("UNKNOWN", result.batches().getFirst().status());
      assertEquals(0, result.created());
      assertTrue(result.batches().getFirst().error().contains("Replay"));
    } finally {
      service.close();
    }
  }

  @Test
  void oneFileAtATimeAndNoMoreThanFourWorkers() throws Exception {
    var writer = mock(ImportBatchWriter.class);
    var service = new CatalogImportService(writer);
    var started = new CountDownLatch(4);
    var release = new CountDownLatch(1);
    var running = new AtomicInteger();
    var peak = new AtomicInteger();
    when(writer.write(anyList())).thenAnswer(invocation -> {
      int n = running.incrementAndGet();
      peak.accumulateAndGet(n, Math::max);
      started.countDown();
      try {
        if (
          !release.await(10, TimeUnit.SECONDS)
        ) throw new IllegalStateException("Test latch timed out");
        return new ImportBatchWriter.Counts(50, 0);
      } finally {
        running.decrementAndGet();
      }
    });
    var csv = new StringBuilder(
      "productId,name,description,price,currency,stock\n"
    );
    for (int n = 1; n <= 1000; n++) csv.append(
      String.format(
        "20000000-0000-0000-0000-%012d,Name,Description,1.00,INR,1\n",
        n
      )
    );
    var file = new MockMultipartFile(
      "file",
      "batch.csv",
      "text/csv",
      csv.toString().getBytes(StandardCharsets.UTF_8)
    );
    try (var coordinator = Executors.newSingleThreadExecutor()) {
      var first = coordinator.submit(() -> service.run(file));
      try {
        assertTrue(started.await(5, TimeUnit.SECONDS));
        var rejected = assertThrows(ResponseStatusException.class, () ->
          service.run(file)
        );
        assertEquals(429, rejected.getStatusCode().value());
        release.countDown();
        var result = first.get(10, TimeUnit.SECONDS);
        assertEquals(1000, result.created());
        assertEquals(20, result.committedBatches());
        assertEquals(4, peak.get());
        verify(writer, times(20)).write(anyList());
      } finally {
        release.countDown();
      }
    } finally {
      service.close();
    }
  }
}
