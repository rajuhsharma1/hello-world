package com.ecal.catalog.internal.importing;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.commons.csv.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Synchronous HTTP coordination, bounded parallel database work, independently committed batches. */
@Service
public class CatalogImportService {

  public static final int BATCH_SIZE = 50,
    MAX_ROWS = 1000,
    MAX_BYTES = 262144;
  private final ImportBatchWriter writer;
  private final Semaphore admission = new Semaphore(1);
  private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
    4,
    4,
    0,
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(16),
    new ThreadPoolExecutor.AbortPolicy()
  );

  public CatalogImportService(ImportBatchWriter writer) {
    this.writer = writer;
  }

  public Result run(MultipartFile file) {
    if (!admission.tryAcquire()) throw new ResponseStatusException(
      HttpStatus.TOO_MANY_REQUESTS,
      "An import is already running. Retry later."
    );
    try {
      var rows = parse(file);
      List<CompletableFuture<Batch>> pending = new ArrayList<>();
      // At most 20 tasks exist: four running plus sixteen queued. No common pool or unbounded queue.
      for (int offset = 0; offset < rows.size(); offset += BATCH_SIZE) {
        var batch = List.copyOf(
          rows.subList(offset, Math.min(offset + BATCH_SIZE, rows.size()))
        );
        int index = pending.size() + 1;
        pending.add(
          CompletableFuture.supplyAsync(
            () -> {
              try {
                var c = writer.write(batch);
                return new Batch(
                  index,
                  "COMMITTED",
                  batch.size(),
                  c.created(),
                  c.skipped(),
                  null
                );
              } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                  "Catalog import batch failed; batch={}, exceptionType={}",
                  index,
                  e.getClass().getSimpleName()
                );
                return new Batch(
                  index,
                  e instanceof IllegalArgumentException
                    ? "ROLLED_BACK"
                    : "UNKNOWN",
                  batch.size(),
                  0,
                  0,
                  e instanceof IllegalArgumentException
                    ? "Batch rolled back. Check field limits and existing product IDs; correct the file and retry. No rows in this batch committed."
                    : "Batch outcome could not be verified. Replay the same file; already committed rows will be skipped."
                );
              }
            },
            workers
          )
        );
      }
      var results = pending.stream().map(CompletableFuture::join).toList();
      return new Result(
        rows.size(),
        results.stream().mapToInt(Batch::created).sum(),
        results.stream().mapToInt(Batch::skipped).sum(),
        (int) results
          .stream()
          .filter(x -> x.status().equals("COMMITTED"))
          .count(),
        (int) results
          .stream()
          .filter(x -> !x.status().equals("COMMITTED"))
          .count(),
        results
      );
    } finally {
      admission.release();
    }
  }

  static List<ImportRow> parse(MultipartFile file) {
    if (
      file.isEmpty() || file.getSize() > MAX_BYTES
    ) throw new IllegalArgumentException("CSV must be 1–262144 bytes.");
    var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(
      CodingErrorAction.REPORT
    );
    try (
      var reader = new InputStreamReader(file.getInputStream(), decoder);
      var csv = CSVFormat.RFC4180.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
        .get()
        .parse(reader)
    ) {
      if (
        !csv
          .getHeaderNames()
          .equals(
            List.of(
              "productId",
              "name",
              "description",
              "price",
              "currency",
              "stock"
            )
          )
      ) throw new IllegalArgumentException(
        "CSV headers must match the sample."
      );
      List<ImportRow> rows = new ArrayList<>();
      Set<UUID> ids = new HashSet<>();
      for (var record : csv) {
        if (
          rows.size() == MAX_ROWS || !record.isConsistent()
        ) throw new IllegalArgumentException(
          "CSV must contain at most 1000 consistent rows."
        );
        UUID id = UUID.fromString(record.get(0));
        if (!ids.add(id)) throw new IllegalArgumentException(
          "Duplicate product ID in file."
        );
        rows.add(
          new ImportRow(
            (int) record.getRecordNumber() + 1,
            id,
            record.get(1),
            record.get(2),
            record.get(3),
            record.get(4),
            record.get(5)
          )
        );
      }
      if (rows.isEmpty()) throw new IllegalArgumentException(
        "CSV needs at least one data row."
      );
      return rows;
    } catch (IOException | UncheckedIOException e) {
      throw new IllegalArgumentException("Malformed UTF-8 CSV.", e);
    }
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    workers.shutdown();
    try {
      if (
        !workers.awaitTermination(15, TimeUnit.SECONDS)
      ) workers.shutdownNow();
    } catch (InterruptedException e) {
      workers.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public record Batch(
    int index,
    String status,
    int rows,
    int created,
    int skipped,
    String error
  ) {}

  public record Result(
    int totalRows,
    int created,
    int skipped,
    int committedBatches,
    int failedBatches,
    List<Batch> batches
  ) {}
}
