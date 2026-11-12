"use client";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { ProductUnit, ProductUnitPayload, Unit } from "@/Types";

/** One editable pack-size row; numbers are kept as strings while typing. */
export interface PackRow { unitId: string; factor: string; sellingPrice: string; barcode: string; }

export const toPackRows = (list: ProductUnit[]): PackRow[] =>
  list.map((pu) => ({
    unitId: String(pu.unit.id),
    factor: String(pu.conversionFactor),
    sellingPrice: pu.sellingPrice != null ? String(pu.sellingPrice) : "",
    barcode: pu.barcode ?? "",
  }));

/** Returns the payload, or an error message if a row is incomplete. */
export function toPackPayload(rows: PackRow[]): ProductUnitPayload[] | string {
  const out: ProductUnitPayload[] = [];
  for (const r of rows) {
    if (!r.unitId) return "Choose a unit for every pack size";
    const factor = Number(r.factor);
    if (!(factor > 0)) return "Pack size quantity must be greater than zero";
    const price = r.sellingPrice.trim() ? Number(r.sellingPrice) : undefined;
    if (price !== undefined && !(price > 0)) return "Pack price must be greater than zero";
    out.push({
      unitId: Number(r.unitId),
      conversionFactor: factor,
      sellingPrice: price,
      barcode: r.barcode.trim() || undefined,
    });
  }
  return out;
}

interface Props {
  units: Unit[];
  baseUnitId?: number;
  rows: PackRow[];
  onChange: (rows: PackRow[]) => void;
}

export function PackSizesEditor({ units, baseUnitId, rows, onChange }: Props) {
  const base = units.find((u) => u.id === baseUnitId);
  const update = (i: number, patch: Partial<PackRow>) =>
    onChange(rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  if (!base) {
    return <p className="text-sm text-gray-500">Choose a base unit first, then add pack sizes such as cartons or boxes.</p>;
  }

  return (
    <div className="space-y-3">
      {rows.length > 0 && (
        <div className="hidden sm:grid grid-cols-12 gap-2 text-xs font-medium text-gray-500">
          <div className="col-span-3">Pack</div>
          <div className="col-span-3">Contains ({base.name})</div>
          <div className="col-span-2">Pack price</div>
          <div className="col-span-3">Pack barcode</div>
        </div>
      )}
      {rows.map((r, i) => (
        <div key={i} className="grid grid-cols-12 gap-2 items-center">
          <div className="col-span-12 sm:col-span-3">
            <Select value={r.unitId} onValueChange={(v) => update(i, { unitId: v })}>
              <SelectTrigger className="h-9"><SelectValue placeholder="Unit" /></SelectTrigger>
              <SelectContent>
                {units.filter((u) => u.id !== baseUnitId).map((u) => (
                  <SelectItem key={u.id} value={String(u.id)}>{u.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="col-span-4 sm:col-span-3">
            <Input type="number" min="0" step="any" placeholder="e.g. 24" className="h-9"
              value={r.factor} onChange={(e) => update(i, { factor: e.target.value })} aria-label="Quantity per pack" />
          </div>
          <div className="col-span-3 sm:col-span-2">
            <Input type="number" min="0" step="0.01" placeholder="Auto" className="h-9"
              value={r.sellingPrice} onChange={(e) => update(i, { sellingPrice: e.target.value })} aria-label="Pack price" />
          </div>
          <div className="col-span-4 sm:col-span-3">
            <Input placeholder="Optional" className="h-9"
              value={r.barcode} onChange={(e) => update(i, { barcode: e.target.value })} aria-label="Pack barcode" />
          </div>
          <div className="col-span-1 flex justify-center">
            <button type="button" onClick={() => onChange(rows.filter((_, idx) => idx !== i))}
              className="text-red-400 hover:text-red-600" aria-label="Remove pack size">
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>
      ))}
      <Button type="button" variant="outline" size="sm"
        onClick={() => onChange([...rows, { unitId: "", factor: "", sellingPrice: "", barcode: "" }])}>
        <Plus className="h-4 w-4" /> Add pack size
      </Button>
      <p className="text-xs text-gray-500">
        Stock is always counted in {base.name}. Leave pack price empty to use the {base.name.toLowerCase()} price × quantity.
      </p>
    </div>
  );
}
