import type { Product } from "../api";
import { money } from "../format";
/** Catalog presentation is independent of checkout transport and transaction recovery. */
export function ProductCard({
  product: p,
  index: i,
  selected,
  locked,
  onChoose,
}: {
  product: Product;
  index: number;
  selected: boolean;
  locked: boolean;
  onChoose: (id: string) => void;
}) {
  return (
    <article className={`product-card ${selected ? "selected" : ""}`}>
      <div className={`product-art art-${i % 3}`} aria-hidden="true">
        <div className="product-object">
          {p.name.toLowerCase().includes("notebook") ? (
            <div className="notebook">
              <span>
                FIELD
                <br />
                NOTES
              </span>
            </div>
          ) : p.name.toLowerCase().includes("mug") ? (
            <div className="travel-mug">
              <span>EVERYDAY</span>
            </div>
          ) : (
            <div className="desk-lamp">
              <span />
            </div>
          )}
        </div>
        <span className="collection-tag">
          THE EVERYDAY COLLECTION / {String(i + 1).padStart(2, "0")}
        </span>
      </div>
      <div className="product-info">
        <div className="product-meta">
          <span className="eyebrow">ECAL ESSENTIALS</span>
          <span className={`stock ${p.availableStock === 0 ? "out" : ""}`}>
            {p.availableStock > 0
              ? `${p.availableStock} available`
              : "Out of stock"}
          </span>
        </div>
        <h3>{p.name}</h3>
        <p>{p.description}</p>
        <div className="product-bottom">
          <strong>{money(p.price, p.currency)}</strong>
          <button
            aria-label={`${selected ? "Selected" : "Choose"} ${p.name}`}
            aria-pressed={selected}
            disabled={locked || p.availableStock === 0}
            onClick={() => onChoose(p.id)}
          >
            {selected ? "Selected" : "Choose"}
            <span className="sr-only"> {p.name}</span>
            <span aria-hidden="true"> +</span>
          </button>
        </div>
      </div>
    </article>
  );
}
