"use client";
import { useEffect, useId, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { useProductSearch } from "@/hooks/useProductSearch";
import { cn } from "@/lib/utils";
import type { Product } from "@/Types";

/**
 * Pick a product by typing its name, SKU or barcode. Searches the server, so it works for
 * catalogues of any size (the old dropdowns only held the first 100–200 products).
 */
export function ProductPicker({ businessId, value, onChange, placeholder = "Search products…", className, autoFocus, clearOnPick }: {
  businessId: number;
  value: Product | null;
  onChange: (p: Product | null) => void;
  placeholder?: string;
  className?: string;
  autoFocus?: boolean;
  /** For "add to list" pickers: empty the box after each pick. */
  clearOnPick?: boolean;
}) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [cursor, setCursor] = useState(0);
  const { results, loading } = useProductSearch(open ? businessId : undefined, query, 20);
  const boxRef = useRef<HTMLDivElement>(null);
  const listId = useId();

  useEffect(() => {
    const close = (e: MouseEvent) => { if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const pick = (p: Product) => {
    onChange(p);
    setOpen(false);
    setQuery("");
    setCursor(0);
  };

  const shown = open ? query : clearOnPick ? "" : value?.productName ?? "";

  return (
    <div ref={boxRef} className={cn("relative", className)}>
      <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-3" />
      <input
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        autoFocus={autoFocus}
        value={shown}
        placeholder={value && !clearOnPick ? value.productName : placeholder}
        onFocus={() => { setOpen(true); setQuery(""); }}
        onChange={(e) => { setQuery(e.target.value); setOpen(true); setCursor(0); }}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") { e.preventDefault(); setOpen(true); setCursor((c) => Math.min(c + 1, results.length - 1)); }
          else if (e.key === "ArrowUp") { e.preventDefault(); setCursor((c) => Math.max(c - 1, 0)); }
          else if (e.key === "Enter" && open && results[cursor]) { e.preventDefault(); pick(results[cursor]); }
          else if (e.key === "Escape") setOpen(false);
        }}
        className="h-10 w-full rounded-xl border border-line bg-surface pl-10 pr-9 text-sm text-ink outline-none placeholder:text-ink-3 focus:border-brand focus:ring-2 focus:ring-brand/30"
      />
      {value && !clearOnPick && !open && (
        <button type="button" aria-label="Clear product" onClick={() => onChange(null)}
          className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-ink-3 hover:text-ink">
          <X className="h-4 w-4" />
        </button>
      )}
      {open && (
        <ul id={listId} role="listbox"
          className="absolute z-50 mt-1 max-h-72 w-full overflow-y-auto rounded-2xl border border-line bg-surface p-1.5 shadow-float">
          {results.map((p, i) => (
            <li key={p.id} role="option" aria-selected={i === cursor}>
              <button type="button" onMouseEnter={() => setCursor(i)} onMouseDown={(e) => e.preventDefault()} onClick={() => pick(p)}
                className={cn("flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2 text-left text-sm", i === cursor && "bg-surface-2")}>
                <span className="min-w-0">
                  <span className="block truncate font-medium text-ink">{p.productName}</span>
                  <span className="block truncate text-xs text-ink-3">{p.sku ? `SKU ${p.sku}` : "No SKU"}{p.barcode ? ` · ${p.barcode}` : ""}</span>
                </span>
                <span className="shrink-0 text-xs text-ink-3">{p.totalCount} in stock</span>
              </button>
            </li>
          ))}
          {!loading && results.length === 0 && <li className="px-3 py-3 text-sm text-ink-3">No products match</li>}
          {loading && results.length === 0 && <li className="px-3 py-3 text-sm text-ink-3">Searching…</li>}
        </ul>
      )}
    </div>
  );
}
