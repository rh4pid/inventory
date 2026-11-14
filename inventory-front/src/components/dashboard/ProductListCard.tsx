"use client";
import { useMemo, useState } from "react";
import Link from "next/link";
import { Download, MoreHorizontal, Plus, Search, SlidersHorizontal, Zap } from "lucide-react";
import { cn, formatCurrency } from "@/lib/utils";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import type { DashboardData } from "@/Types";
import { useCan } from "@/lib/access";
import { Panel, Monogram, fmtNum } from "./Panel";

type Row = DashboardData["products"][number];
const ALL = "__all";
const PAGE = 8;

const DEMAND: Record<Row["demand"], { label: string; cls: string; hint: string }> = {
  HIGH: { label: "High", cls: "bg-brand-strong text-white", hint: "Among the top 30% of sellers this period" },
  NORMAL: { label: "Normal", cls: "bg-surface-2 text-ink-2", hint: "Sold this period" },
  LOW: { label: "Low", cls: "bg-accent text-on-accent", hint: "No sales this period" },
};

const STOCK: Record<Row["stockStatus"], string> = { IN: "In stock", LOW: "Low stock", OUT: "Out of stock", DEAD: "Dead stock" };

function toCsv(rows: Row[], withCosts: boolean) {
  const esc = (v: unknown) => {
    const s = v == null ? "" : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const head = ["Product", "SKU", "Supplier", "Category", ...(withCosts ? ["Cost"] : []), "On hand", "Unit", ...(withCosts ? ["On hand value"] : []), "Sold in period", "Stock", "Demand"];
  const body = rows.map((r) => [r.productName, r.sku, r.supplier, r.category, ...(withCosts ? [r.cost] : []), r.onHand, r.unit,
    ...(withCosts ? [(r.onHandValue ?? 0).toFixed(2)] : []), r.soldInPeriod, STOCK[r.stockStatus], DEMAND[r.demand].label]);
  return [head, ...body].map((line) => line.map(esc).join(",")).join("\n");
}

/** The product list from the sample: search, filters, value on hand and a demand badge per product. */
export function ProductListCard({ rows, locationName }: { rows: Row[]; locationName: string }) {
  const can = useCan();
  const seeCosts = can("VIEW_COSTS");
  const [q, setQ] = useState("");
  const [category, setCategory] = useState(ALL);
  const [supplier, setSupplier] = useState(ALL);
  const [stock, setStock] = useState(ALL);
  const [page, setPage] = useState(0);

  const categories = useMemo(() => [...new Set(rows.map((r) => r.category).filter(Boolean) as string[])].sort(), [rows]);
  const suppliers = useMemo(() => [...new Set(rows.map((r) => r.supplier).filter(Boolean) as string[])].sort(), [rows]);

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    return rows.filter((r) =>
      (!term || r.productName.toLowerCase().includes(term) || (r.sku ?? "").toLowerCase().includes(term)) &&
      (category === ALL || r.category === category) &&
      (supplier === ALL || r.supplier === supplier) &&
      (stock === ALL || r.stockStatus === stock));
  }, [rows, q, category, supplier, stock]);

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE));
  const current = Math.min(page, pages - 1);
  const visible = filtered.slice(current * PAGE, current * PAGE + PAGE);
  const filtering = q || category !== ALL || supplier !== ALL || stock !== ALL;
  const totalValue = filtered.reduce((s, r) => s + (r.onHandValue ?? 0), 0);

  const reset = () => { setQ(""); setCategory(ALL); setSupplier(ALL); setStock(ALL); setPage(0); };
  const exportCsv = () => {
    const blob = new Blob([toCsv(filtered, seeCosts)], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `products-${locationName.toLowerCase().replace(/\W+/g, "-")}-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const filterSelect = (label: string, value: string, set: (v: string) => void, options: { value: string; label: string }[]) => (
    <Select value={value} onValueChange={(v) => { set(v); setPage(0); }}>
      <SelectTrigger className="h-11 w-full rounded-2xl bg-surface-2/60 sm:w-40" aria-label={label}>
        <SelectValue placeholder={label} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>All {label.toLowerCase()}</SelectItem>
        {options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
      </SelectContent>
    </Select>
  );

  return (
    <Panel className="p-0 md:p-0">
      <div className="flex flex-col gap-3 p-5 md:p-6 xl:flex-row xl:items-center">
        <div className="min-w-0 xl:mr-auto">
          <h2 className="text-xl font-semibold tracking-tight text-ink">Product list</h2>
          <p className="text-[13px] text-ink-3">
            {fmtNum(filtered.length)} product{filtered.length === 1 ? "" : "s"}{seeCosts ? ` · ${formatCurrency(totalValue)} on hand` : ""} · {locationName}
          </p>
        </div>
        <div className="flex flex-col gap-2.5 sm:flex-row sm:flex-wrap sm:items-center">
          <label className="relative block sm:w-56">
            <span className="sr-only">Search products</span>
            <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-3" />
            <input
              value={q}
              onChange={(e) => { setQ(e.target.value); setPage(0); }}
              placeholder="Search…"
              className="h-11 w-full rounded-2xl border border-line bg-surface-2/60 pl-10 pr-3 text-sm text-ink outline-none placeholder:text-ink-3 focus:border-brand focus:ring-2 focus:ring-brand/30"
            />
          </label>
          {filterSelect("Category", category, setCategory, categories.map((c) => ({ value: c, label: c })))}
          {filterSelect("Supplier", supplier, setSupplier, suppliers.map((s) => ({ value: s, label: s })))}
          {filterSelect("Stock", stock, setStock, (Object.keys(STOCK) as Row["stockStatus"][]).map((k) => ({ value: k, label: STOCK[k] })))}
          <div className="flex gap-2">
            <button onClick={reset} disabled={!filtering} aria-label="Clear filters" title="Clear filters"
              className="flex h-11 w-11 items-center justify-center rounded-2xl border border-line bg-surface text-ink-2 hover:text-ink disabled:opacity-40">
              <SlidersHorizontal className="h-4 w-4" />
            </button>
            <button onClick={exportCsv} disabled={filtered.length === 0} aria-label="Export to CSV" title="Export to CSV"
              className="flex h-11 w-11 items-center justify-center rounded-2xl border border-line bg-surface text-ink-2 hover:text-ink disabled:opacity-40">
              <Download className="h-4 w-4" />
            </button>
            {can("MANAGE_CATALOG") && <Link href="/products/new" aria-label="Add product" title="Add product"
              className="flex h-11 w-11 items-center justify-center rounded-2xl bg-accent text-on-accent shadow-[var(--shadow-accent)] hover:bg-accent-deep">
              <Plus className="h-5 w-5" />
            </Link>}
          </div>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className={cn("w-full text-sm", seeCosts ? "min-w-[56rem]" : "min-w-[44rem]")}>
          <thead>
            <tr className="text-left text-[13px] text-ink-3">
              <th className="px-5 py-3 font-medium md:pl-6">Product</th>
              <th className="px-4 py-3 font-medium">Supplier</th>
              <th className="px-4 py-3 font-medium">Category</th>
              {seeCosts && <th className="px-4 py-3 text-right font-medium">Cost</th>}
              <th className="px-4 py-3 text-right font-medium">On hand</th>
              {seeCosts && <th className="px-4 py-3 text-right font-medium">On hand value</th>}
              <th className="px-4 py-3 font-medium">Demand</th>
              <th className="px-5 py-3 text-right font-medium md:pr-6"><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            {visible.map((r) => (
              <tr key={r.productId} className="border-t border-line/70 hover:bg-surface-2/50">
                <td className="px-5 py-3 md:pl-6">
                  <div className="flex items-center gap-3">
                    <Monogram name={r.productName} />
                    <div className="min-w-0">
                      <div className="truncate font-medium text-ink">{r.productName}</div>
                      <div className="text-xs text-ink-3">{r.sku ? `SKU ${r.sku}` : "No SKU"}</div>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3 text-ink-2">{r.supplier ?? <span className="text-ink-3">—</span>}</td>
                <td className="px-4 py-3 text-ink-2">{r.category ?? <span className="text-ink-3">—</span>}</td>
                {seeCosts && <td className="px-4 py-3 text-right tabular-nums text-ink-2">{formatCurrency(r.cost ?? 0)}</td>}
                <td className="px-4 py-3 text-right tabular-nums">
                  <span className={cn("font-medium", r.stockStatus === "OUT" ? "text-accent-deep" : "text-ink")}>{fmtNum(r.onHand)}</span>
                  {r.unit && <span className="ml-1 text-xs text-ink-3">{r.unit}</span>}
                  {(r.stockStatus === "LOW" || r.stockStatus === "OUT" || r.stockStatus === "DEAD") && (
                    <div className="text-[11px] text-ink-3">{STOCK[r.stockStatus]}</div>
                  )}
                </td>
                {seeCosts && <td className="px-4 py-3 text-right font-medium tabular-nums text-ink">{formatCurrency(r.onHandValue ?? 0)}</td>}
                <td className="px-4 py-3">
                  <span className={cn("inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold", DEMAND[r.demand].cls)}
                    title={`${DEMAND[r.demand].hint} · ${fmtNum(r.soldInPeriod)} sold`}>
                    <Zap className="h-3 w-3" />{DEMAND[r.demand].label}
                  </span>
                </td>
                <td className="px-5 py-3 text-right md:pr-6">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <button aria-label={`Actions for ${r.productName}`}
                        className="inline-flex h-9 w-9 items-center justify-center rounded-xl text-ink-3 hover:bg-surface-2 hover:text-ink">
                        <MoreHorizontal className="h-5 w-5" />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="w-44">
                      <DropdownMenuItem asChild><Link href="/inventory">View stock</Link></DropdownMenuItem>
                      {can("SELL") && <DropdownMenuItem asChild><Link href="/pos">Sell at POS</Link></DropdownMenuItem>}
                      {can("MANAGE_CATALOG") && <DropdownMenuItem asChild><Link href={`/products?q=${encodeURIComponent(r.productName)}`}>Edit product</Link></DropdownMenuItem>}
                    </DropdownMenuContent>
                  </DropdownMenu>
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr>
                <td colSpan={seeCosts ? 8 : 6} className="px-6 py-10 text-center text-sm text-ink-3">
                  {rows.length === 0 ? "No stock here yet. Receive stock or record a purchase to see products." : "No products match these filters."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {pages > 1 && (
        <div className="flex items-center justify-between border-t border-line/70 px-5 py-3 text-xs text-ink-3 md:px-6">
          <span>{current * PAGE + 1}–{Math.min((current + 1) * PAGE, filtered.length)} of {filtered.length}</span>
          <div className="flex gap-2">
            <button disabled={current === 0} onClick={() => setPage(current - 1)}
              className="h-9 rounded-xl border border-line bg-surface px-3 font-medium text-ink-2 disabled:opacity-40">Previous</button>
            <button disabled={current >= pages - 1} onClick={() => setPage(current + 1)}
              className="h-9 rounded-xl border border-line bg-surface px-3 font-medium text-ink-2 disabled:opacity-40">Next</button>
          </div>
        </div>
      )}
    </Panel>
  );
}
