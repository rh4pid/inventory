"use client";
import { errorMessage } from "@/lib/utils";
import { confirmAction } from "@/context/ConfirmProvider";
import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Plus } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { products as productsApi, stockCounts as countsApi } from "@/lib/api";
import { fmtQty } from "@/constants/Inventory";
import { COUNT_STATUS } from "@/constants/Inventory";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Product, StockCountView } from "@/Types";
import { useCan } from "@/lib/access";

export default function StockCountPage() {
  const params = useParams<{ id: string }>();
  const { activeBusiness } = useBusiness();
  const [view, setView] = useState<StockCountView | null>(null);
  /** Unsaved edits: lineId → text in the box ("" = not counted). */
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [products, setProducts] = useState<Product[]>([]);
  const [addId, setAddId] = useState("");
  const [busy, setBusy] = useState(false);
  const isManager = useCan()("APPROVE");
  const businessId = activeBusiness?.businessId;
  const countId = Number(params.id);

  const load = useCallback(async () => {
    if (!businessId) return;
    try {
      const [v, p] = await Promise.all([
        countsApi.get(businessId, countId),
        productsApi.list(businessId, { size: 500 }),
      ]);
      setView(v);
      setProducts(p.content);
      setDrafts({});
    } catch (err) { toast.error(errorMessage(err)); }
  }, [businessId, countId]);

  useEffect(() => { load(); }, [load]);

  const editable = view?.count.status === "OPEN";
  const dirty = Object.keys(drafts).length > 0;

  const valueOf = useCallback((lineId: number, counted: number | null) =>
    drafts[lineId] ?? (counted != null ? String(counted) : ""), [drafts]);

  const notOnCount = useMemo(() => {
    const onCount = new Set(view?.lines.map((l) => l.productId));
    return products.filter((p) => !onCount.has(p.id));
  }, [products, view]);

  /** Saves edits; returns the updated view, or null on failure. */
  const save = async (): Promise<StockCountView | null> => {
    if (!businessId || !view) return null;
    if (!dirty) return view;
    const entries = Object.entries(drafts).map(([lineId, text]) => ({
      lineId: Number(lineId),
      countedQuantity: text.trim() === "" ? null : Number(text),
    }));
    if (entries.some((e) => e.countedQuantity != null && !(e.countedQuantity >= 0))) {
      toast.error("Counts must be zero or more");
      return null;
    }
    try {
      const v = await countsApi.saveCounts(businessId, countId, entries);
      setView(v);
      setDrafts({});
      return v;
    } catch (err) { toast.error(errorMessage(err)); return null; }
  };

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    try { await fn(); } finally { setBusy(false); }
  };

  const complete = () => run(async () => {
    const v = await save();
    if (!v || !businessId) return;
    const uncounted = v.totalLines - v.countedLines;
    const action = v.canPost ? "Post this count and update stock" : "Submit this count to a manager";
    const warn = uncounted > 0 ? `${uncounted} product(s) are not counted and will be left unchanged.` : undefined;
    if (!(await confirmAction({ title: `${action}?`, description: warn, confirmLabel: v.canPost ? "Post count" : "Submit" }))) return;
    try {
      const done = await countsApi.complete(businessId, countId);
      setView(done);
      toast.success(done.count.status === "POSTED" ? "Count posted: stock updated" : "Submitted for approval");
    } catch (err) { toast.error(errorMessage(err)); }
  });

  const simple = (fn: (b: number, id: number) => Promise<StockCountView>, msg: string, confirmText?: string) =>
    run(async () => {
      if (!businessId || (confirmText && !(await confirmAction({ title: confirmText, tone: "danger" })))) return;
      try { setView(await fn(businessId, countId)); toast.success(msg); }
      catch (err) { toast.error(errorMessage(err)); }
    });

  const addProduct = () => run(async () => {
    if (!businessId || !addId) return;
    try {
      setView(await countsApi.addProduct(businessId, countId, Number(addId)));
      setAddId("");
    } catch (err) { toast.error(errorMessage(err)); }
  });

  if (!view) return <PageLoader />;
  const { count, blind, lines } = view;
  const status = COUNT_STATUS[count.status];
  const showSystem = !blind;
  const posted = count.status === "POSTED";

  return (
    <div>
      <Link href="/inventory/counts" className="mb-3 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-800">
        <ArrowLeft className="h-4 w-4" /> Stock counts
      </Link>
      <PageHeader
        title={`${count.reference} · ${count.shopName}`}
        description={`${view.countedLines} of ${view.totalLines} products counted${count.notes ? ` · ${count.notes}` : ""}`}
        action={<Badge variant={status.variant}>{status.label}</Badge>}
      />

      {blind && editable && (
        <p className="mb-4 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">
          Blind count: system quantities are hidden so the count stays independent. Enter what you see on the shelf.
        </p>
      )}
      {count.status === "SUBMITTED" && (
        <p className="mb-4 rounded-lg border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-800">
          Submitted by {count.submittedBy}. {view.canPost
            ? "Review the differences and post the count, or send it back for a recount."
            : "Waiting for a manager to post it."}
        </p>
      )}

      <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Product</TableHead>
              {showSystem && <TableHead className="text-right">System</TableHead>}
              <TableHead className="w-40">Counted</TableHead>
              {showSystem && <TableHead className="text-right">Difference</TableHead>}
              {posted && <TableHead className="text-right">Applied</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {lines.map((l) => {
              const text = valueOf(l.id, l.countedQuantity);
              const counted = text.trim() === "" ? null : Number(text);
              const diff = showSystem && counted != null && l.systemQuantity != null ? counted - l.systemQuantity : null;
              return (
                <TableRow key={l.id}>
                  <TableCell className="text-sm">
                    <span className="font-medium">{l.productName}</span>
                    {l.sku && <span className="ml-2 font-mono text-xs text-gray-400">{l.sku}</span>}
                  </TableCell>
                  {showSystem && (
                    <TableCell className="text-right text-sm text-gray-500">
                      {l.systemQuantity != null ? fmtQty(l.systemQuantity) : "—"}
                    </TableCell>
                  )}
                  <TableCell>
                    {editable ? (
                      <div className="flex items-center gap-1.5">
                        <Input
                          type="number" min="0" step="any" inputMode="decimal"
                          aria-label={`Counted quantity of ${l.productName}`}
                          value={text}
                          onChange={(e) => setDrafts((d) => ({ ...d, [l.id]: e.target.value }))}
                          className="h-8 w-24 text-sm"
                        />
                        <span className="text-xs text-gray-400">{l.baseUnit ?? ""}</span>
                      </div>
                    ) : (
                      <span className="text-sm">{l.countedQuantity != null ? fmtQty(l.countedQuantity) : "Not counted"}</span>
                    )}
                  </TableCell>
                  {showSystem && (
                    <TableCell className={`text-right text-sm font-semibold ${
                      diff == null || diff === 0 ? "text-gray-400" : diff < 0 ? "text-red-600" : "text-green-700"}`}>
                      {diff == null ? "—" : `${diff > 0 ? "+" : ""}${fmtQty(diff)}`}
                    </TableCell>
                  )}
                  {posted && (
                    <TableCell className="text-right text-sm">
                      {l.postedDifference != null ? `${l.postedDifference > 0 ? "+" : ""}${fmtQty(l.postedDifference)}` : "—"}
                    </TableCell>
                  )}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>

      {editable && notOnCount.length > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span className="text-sm text-gray-500">Found something not on the list?</span>
          <Select value={addId} onValueChange={setAddId}>
            <SelectTrigger className="h-9 w-64"><SelectValue placeholder="Add a product" /></SelectTrigger>
            <SelectContent>
              {notOnCount.map((p) => <SelectItem key={p.id} value={String(p.id)}>{p.productName}</SelectItem>)}
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={addProduct} disabled={!addId || busy}>
            <Plus className="h-4 w-4" /> Add
          </Button>
        </div>
      )}

      <div className="mt-6 flex flex-wrap gap-2">
        {editable && (
          <>
            <Button variant="outline" onClick={() => run(async () => { if (await save()) toast.success("Counts saved"); })}
              disabled={!dirty || busy}>
              Save progress
            </Button>
            <Button onClick={complete} disabled={busy}>
              {view.canPost ? "Post count" : "Submit for approval"}
            </Button>
          </>
        )}
        {count.status === "SUBMITTED" && view.canPost && (
          <>
            <Button onClick={complete} disabled={busy}>Post count</Button>
            {isManager && (
              <Button variant="outline" onClick={() => simple(countsApi.reopen, "Sent back for recount")} disabled={busy}>
                Send back for recount
              </Button>
            )}
          </>
        )}
        {(count.status === "OPEN" || count.status === "SUBMITTED") && (
          <Button variant="ghost" className="text-red-600" disabled={busy}
            onClick={() => simple(countsApi.cancel, "Count cancelled", "Cancel this count? Nothing will change in stock.")}>
            Cancel count
          </Button>
        )}
      </div>
    </div>
  );
}
