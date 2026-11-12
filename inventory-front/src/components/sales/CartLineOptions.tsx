"use client";
import { useEffect, useState } from "react";
import { inventory as invApi, units as unitsApi } from "@/lib/api";
import type { Product, ProductUnit, StockBatch } from "@/Types";

/** One line in a sale cart. unitId/batchId are omitted for the base unit / automatic batch picking. */
export interface CartLine {
  key: string;
  product: Product;
  quantity: number;
  unitPrice: number;
  unitId?: number;
  unitName?: string;
  batchId?: number;
}

let seq = 0;
export const newLine = (product: Product): CartLine => ({
  key: `${product.id}-${++seq}`,
  product,
  quantity: 1,
  unitPrice: product.sellingPrice,
});

/** Adds a product: bumps the existing plain line (base unit, automatic batch) or starts a new one. */
export function addProduct(cart: CartLine[], product: Product): CartLine[] {
  const plain = cart.find((l) => l.product.id === product.id && !l.unitId && !l.batchId);
  if (plain) return cart.map((l) => (l === plain ? { ...l, quantity: l.quantity + 1 } : l));
  return [...cart, newLine(product)];
}

/** Price of one pack: its own selling price if set, otherwise the base price times the pack size. */
export function packPrice(product: Product, pack?: ProductUnit): number {
  if (!pack) return product.sellingPrice;
  if (pack.sellingPrice != null) return pack.sellingPrice;
  return Math.round(product.sellingPrice * pack.conversionFactor * 100) / 100;
}

const fmt = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(2));

/**
 * Pack-size and batch pickers for a cart line. Only shown when the business sells in pack sizes
 * or tracks batches, and only when the product actually has packs / batches at this location.
 */
export function CartLineOptions({
  businessId, shopId, line, multipleUnits, trackBatches, trackExpiry, onChange, compact,
}: {
  businessId: number;
  shopId: number | null;
  line: CartLine;
  multipleUnits: boolean;
  trackBatches: boolean;
  trackExpiry: boolean;
  onChange: (line: CartLine) => void;
  compact?: boolean;
}) {
  const [packs, setPacks] = useState<ProductUnit[]>([]);
  const [batches, setBatches] = useState<StockBatch[]>([]);
  const productId = line.product.id;

  useEffect(() => {
    if (!multipleUnits) return;
    let live = true;
    unitsApi.forProduct(businessId, productId).then((p) => { if (live) setPacks(p); }).catch(() => {});
    return () => { live = false; };
  }, [businessId, productId, multipleUnits]);

  useEffect(() => {
    if (!trackBatches || !shopId) { setBatches([]); return; }
    let live = true;
    invApi.batches(businessId, { productId, shopId })
      .then((b) => { if (live) setBatches(b.filter((x) => x.quantityRemaining > 0)); })
      .catch(() => {});
    return () => { live = false; };
  }, [businessId, productId, shopId, trackBatches]);

  // A batch picked at another location no longer applies.
  useEffect(() => {
    if (line.batchId && trackBatches && batches.length > 0 && !batches.some((b) => b.id === line.batchId)) {
      onChange({ ...line, batchId: undefined });
    }
  }, [batches]); // eslint-disable-line react-hooks/exhaustive-deps

  if (packs.length === 0 && batches.length === 0) return null;
  const baseName = line.product.unit?.name ?? "unit";
  const cls = `rounded border border-gray-200 bg-white ${compact ? "px-1 py-0.5 text-[11px]" : "px-2 py-1 text-xs"} max-w-[11rem]`;

  return (
    <div className="mt-1 flex flex-wrap gap-1.5">
      {packs.length > 0 && (
        <select
          aria-label={`Pack size for ${line.product.productName}`}
          className={cls}
          value={line.unitId ?? ""}
          onChange={(e) => {
            const pack = packs.find((p) => String(p.unit.id) === e.target.value);
            onChange({
              ...line,
              unitId: pack?.unit.id,
              unitName: pack?.unit.name,
              unitPrice: packPrice(line.product, pack),
            });
          }}
        >
          <option value="">{baseName}</option>
          {packs.map((p) => (
            <option key={p.unit.id} value={p.unit.id}>
              {p.unit.name} ({fmt(p.conversionFactor)} {baseName})
            </option>
          ))}
        </select>
      )}
      {batches.length > 0 && (
        <select
          aria-label={`Batch for ${line.product.productName}`}
          className={cls}
          value={line.batchId ?? ""}
          onChange={(e) => onChange({ ...line, batchId: e.target.value ? Number(e.target.value) : undefined })}
        >
          <option value="">{trackExpiry ? "Auto batch (earliest expiry)" : "Auto batch (oldest first)"}</option>
          {batches.map((b) => (
            <option key={b.id} value={b.id} disabled={trackExpiry && b.status === "EXPIRED"}>
              {b.batchNumber} · {fmt(b.quantityRemaining)} left
              {b.expiryDate ? ` · exp ${b.expiryDate}` : ""}
              {b.status === "EXPIRED" ? " (expired)" : ""}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}
