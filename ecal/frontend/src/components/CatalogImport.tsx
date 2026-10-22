import { useState } from "react";
import { api } from "../api";
import type { ImportResult } from "../api";
export function CatalogImport({ onImported }: { onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const [result, setResult] = useState<ImportResult | null>(null);
  async function submit(event: React.SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || busy) return;
    setBusy(true);
    setError("");
    setResult(null);
    try {
      if (file.size > 262144) throw new Error("Choose a CSV up to 256 KiB.");
      setResult(await api.importCatalog(file));
      onImported();
    } catch (e) {
      setError(
        (e instanceof Error ? e.message : "Import could not complete.") +
          " Some batches may have committed. Replay the same file to safely skip existing products.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="section-block"
      id="import"
      aria-labelledby="import-title"
    >
      <p className="eyebrow">04 / CATALOG OPERATIONS</p>
      <h2 id="import-title">Import, with clear outcomes.</h2>
      <p>
        Upload up to 1,000 products (256 KiB). Each batch of 50 commits
        independently; a failed batch rolls back together. Existing products
        with identical original import data are skipped, and current stock is
        preserved.
      </p>
      <p>
        <a href="/examples/catalog-import.csv" download>
          Download example CSV
        </a>{" "}
        · INR prices, up to two decimal places; stock 0–1,000,000. Use stable,
        unique product UUIDs.
      </p>
      <form onSubmit={submit}>
        <label htmlFor="catalog-csv">Catalog CSV</label>
        <input
          id="catalog-csv"
          type="file"
          accept=".csv,text/csv"
          required
          disabled={busy}
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        <button className="primary-button" disabled={!file || busy}>
          {busy ? "Importing batches…" : "Import catalog"}
        </button>
      </form>
      {error && (
        <p role="alert" className="notice error">
          {error}
        </p>
      )}
      {result && (
        <div role="status" className="notice">
          <strong>
            {result.failedBatches
              ? "Import partially completed"
              : "Import completed"}
          </strong>
          <p>
            {result.created} created · {result.skipped} skipped ·{" "}
            {result.failedBatches} failed batches.
          </p>
          {result.batches.map((b) => (
            <p key={b.index}>
              Batch {b.index}: {b.status} ({b.rows} rows). {b.error}
            </p>
          ))}
          {result.failedBatches > 0 && (
            <p>
              Correct failed rows and replay the file. Keep successful product
              IDs and their original data unchanged.
            </p>
          )}
        </div>
      )}
    </section>
  );
}
