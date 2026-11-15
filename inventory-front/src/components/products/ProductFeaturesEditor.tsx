"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Plus, X } from "lucide-react";
import { toast } from "sonner";
import { features as featuresApi } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Feature, ProductFeatureValue } from "@/Types";

export interface FeatureRow { featureId: number | ""; value: string; }

export const toFeatureRows = (values: ProductFeatureValue[]): FeatureRow[] =>
  values.map((v) => ({ featureId: v.featureId, value: v.value }));

/** Rows to send: complete ones only. Returns an error message when a feature is chosen twice. */
export function toFeaturePayload(rows: FeatureRow[]): { featureId: number; value: string }[] | string {
  const out: { featureId: number; value: string }[] = [];
  const seen = new Set<number>();
  for (const r of rows) {
    if (r.featureId === "" || !r.value.trim()) continue;
    if (seen.has(r.featureId)) return "Each feature can only be given once";
    seen.add(r.featureId);
    out.push({ featureId: r.featureId, value: r.value.trim() });
  }
  return out;
}

const selectClass =
  "h-10 w-full rounded-xl border border-line bg-surface px-3 text-sm text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand";

/**
 * The product form's Features section: pick a feature (Color, Size, …) and type the value.
 * Values already used on other products are suggested. A new feature can be added on the spot.
 */
export function ProductFeaturesEditor({ businessId, rows, onChange }: {
  businessId: number;
  rows: FeatureRow[];
  onChange: (rows: FeatureRow[]) => void;
}) {
  const [list, setList] = useState<Feature[]>([]);
  const [adding, setAdding] = useState(false);
  const [newName, setNewName] = useState("");

  useEffect(() => {
    featuresApi.list(businessId).then(setList).catch(() => setList([]));
  }, [businessId]);

  const set = (i: number, patch: Partial<FeatureRow>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const used = new Set(rows.map((r) => r.featureId).filter((id): id is number => id !== ""));
  const unused = list.filter((f) => !used.has(f.id));

  const addRow = () => onChange([...rows, { featureId: unused[0]?.id ?? "", value: "" }]);

  const createFeature = async () => {
    const name = newName.trim();
    if (!name) return;
    try {
      const f = await featuresApi.create(businessId, name);
      const feature: Feature = { id: f.id, name: f.name, productCount: 0, values: [] };
      setList((l) => [...l, feature].sort((a, b) => a.name.localeCompare(b.name)));
      onChange([...rows, { featureId: feature.id, value: "" }]);
      setNewName("");
      setAdding(false);
    } catch (err) {
      toast.error(errorMessage(err, "Could not add the feature"));
    }
  };

  return (
    <div className="space-y-3">
      {rows.length === 0 && (
        <p className="text-sm text-ink-3">No features yet. Add things like Color or Size so staff can tell similar products apart.</p>
      )}
      {rows.map((r, i) => {
        const feature = list.find((f) => f.id === r.featureId);
        const listId = `feature-values-${i}`;
        return (
          <div key={i} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] items-center gap-2">
            <select aria-label="Feature" className={selectClass} value={r.featureId}
              onChange={(e) => set(i, { featureId: e.target.value ? Number(e.target.value) : "" })}>
              <option value="">Choose feature…</option>
              {list.filter((f) => f.id === r.featureId || !used.has(f.id)).map((f) => (
                <option key={f.id} value={f.id}>{f.name}</option>
              ))}
            </select>
            <Input aria-label={`${feature?.name ?? "Feature"} value`} list={listId} maxLength={120}
              placeholder={feature ? `e.g. ${feature.values[0] ?? (feature.name.toLowerCase() === "size" ? "M" : "Red")}` : "Value"}
              value={r.value} onChange={(e) => set(i, { value: e.target.value })} />
            <datalist id={listId}>{feature?.values.map((v) => <option key={v} value={v} />)}</datalist>
            <Button type="button" variant="ghost" size="icon" className="h-9 w-9" aria-label="Remove feature"
              onClick={() => onChange(rows.filter((_, j) => j !== i))}>
              <X className="h-4 w-4" />
            </Button>
          </div>
        );
      })}

      <div className="flex flex-wrap items-center gap-2">
        {unused.length > 0 && (
          <Button type="button" size="sm" variant="outline" onClick={addRow}><Plus className="h-4 w-4" /> Add a feature</Button>
        )}
        {adding ? (
          <div className="flex items-center gap-2">
            <Input autoFocus className="h-9 w-44" placeholder="New feature, e.g. Material" maxLength={80}
              value={newName} onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); void createFeature(); } if (e.key === "Escape") setAdding(false); }} />
            <Button type="button" size="sm" onClick={() => void createFeature()} disabled={!newName.trim()}>Add</Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setAdding(false)}>Cancel</Button>
          </div>
        ) : (
          <Button type="button" size="sm" variant="ghost" onClick={() => setAdding(true)}>
            <Plus className="h-4 w-4" /> {list.length === 0 ? "Create your first feature" : "New feature"}
          </Button>
        )}
        <Link href="/features" className="ml-auto text-xs font-medium text-brand-deep hover:underline">Manage features</Link>
      </div>
    </div>
  );
}
