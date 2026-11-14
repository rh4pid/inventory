"use client";
import { errorMessage } from "@/lib/utils";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { inventory as invApi, units as unitsApi, products as productsApi } from "@/lib/api";
import { ProductPicker } from "@/components/shared/ProductPicker";
import { ADJUSTMENT_REASONS, STOCK_OUT_REASONS, fmtQty } from "@/constants/Inventory";
import { formatDate } from "@/components/inventory/BatchStatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { isPending, type Product, type ProductUnit, type Shop, type StockActionResponse, type StockBatch, type Supplier } from "@/Types";

export type StockAction = "in" | "out" | "transfer" | "adjust";

const TITLES: Record<StockAction, string> = {
  in: "Stock In", out: "Stock Out", transfer: "Transfer Stock", adjust: "Adjust Stock",
};

const BASE = "base";
const AUTO = "auto";

/** Fields to prefill, e.g. when acting on one batch from the batch list. */
export interface StockActionPreset {
  productId?: number;
  shopId?: number;
  batchId?: number;
}

interface Props {
  action: StockAction | null;
  onClose: () => void;
  onDone: () => void;
  businessId: number;
  /** Optional: products already loaded (only used to name a preset product quickly). */
  products?: Product[];
  shops: Shop[];
  /** Where a transfer may go; defaults to {@link shops}. */
  destinations?: Shop[];
  suppliers: Supplier[];
  preset?: StockActionPreset;
  /** Feature switches from Settings → Features. */
  multipleUnits: boolean;
  trackBatches: boolean;
  trackExpiry: boolean;
  /** Stock-outs and adjustments by this user go to a manager for approval. */
  needsApproval?: boolean;
}

/**
 * Stock in / out / transfer / adjust form. The parent remounts it (via `key`) each time it
 * opens, so state starts fresh from `preset`.
 */
export function StockActionDialog({
  action, onClose, onDone, businessId, products, shops, destinations, suppliers, preset,
  multipleUnits, trackBatches, trackExpiry, needsApproval,
}: Props) {
  const [productId, setProductId] = useState(preset?.productId ? String(preset.productId) : "");
  const [picked, setPicked] = useState<Product | null>(null);
  const [shopId, setShopId] = useState(preset?.shopId ? String(preset.shopId) : "");
  const [toShopId, setToShopId] = useState("");
  const [unit, setUnit] = useState(BASE);
  const [quantity, setQuantity] = useState("");
  const [supplierId, setSupplierId] = useState("");
  const [reason, setReason] = useState("");
  const [notes, setNotes] = useState("");
  const [costPrice, setCostPrice] = useState("");
  const [batchNumber, setBatchNumber] = useState("");
  const [expiryDate, setExpiryDate] = useState("");
  const [batchId, setBatchId] = useState(preset?.batchId ? String(preset.batchId) : AUTO);
  const [batches, setBatches] = useState<StockBatch[]>([]);
  const [packs, setPacks] = useState<ProductUnit[]>([]);
  const [saving, setSaving] = useState(false);
  const latest = useRef("");

  const picksBatch = trackBatches && action !== "in";
  const usesPacks = multipleUnits && action !== "adjust";

  /** Loads pack sizes and batches for a product + location; ignores replies for an older choice. */
  const loadFor = useCallback((pid: string, sid: string) => {
    const key = `${pid}:${sid}`;
    latest.current = key;
    if (usesPacks && pid) {
      unitsApi.forProduct(businessId, Number(pid))
        .then((list) => { if (latest.current === key) setPacks(list); })
        .catch(() => {});
    }
    if (picksBatch && pid && sid) {
      invApi.batches(businessId, { productId: Number(pid), shopId: Number(sid) })
        .then((list) => { if (latest.current === key) setBatches(list); })
        .catch(() => {});
    }
  }, [businessId, usesPacks, picksBatch]);

  // Prefilled product/location (e.g. opened from the batch list): load once on open.
  useEffect(() => {
    if (!preset?.productId) return;
    loadFor(String(preset.productId), preset.shopId ? String(preset.shopId) : "");
    const known = products?.find((p) => p.id === preset.productId);
    if (known) setPicked(known);
    else productsApi.get(businessId, preset.productId).then(setPicked).catch(() => {});
  }, [preset, loadFor]); // eslint-disable-line react-hooks/exhaustive-deps

  const chooseProduct = (p: Product | null) => {
    setPicked(p);
    const id = p ? String(p.id) : "";
    setProductId(id); setUnit(BASE); setBatchId(AUTO); setPacks([]); setBatches([]);
    loadFor(id, shopId);
  };
  const chooseShop = (id: string) => {
    setShopId(id); setBatchId(AUTO); setBatches([]);
    loadFor(productId, id);
  };

  const product = picked ?? undefined;
  const baseName = product?.unit?.name ?? "units";
  const pack = packs.find((p) => String(p.unit.id) === unit);
  const batch = batches.find((b) => String(b.id) === batchId);
  const qty = Number(quantity);
  const baseQty = pack ? qty * pack.conversionFactor : qty;
  const usableNow = batches.filter((b) => !trackExpiry || b.status !== "EXPIRED")
    .reduce((s, b) => s + b.quantityRemaining, 0);

  const needsReason = action === "out" || action === "adjust";
  const reasons = action === "adjust" ? ADJUSTMENT_REASONS : STOCK_OUT_REASONS;

  const valid = useMemo(() => {
    if (!productId || !shopId || quantity === "") return false;
    if (action === "adjust" ? !(qty >= 0) : !(qty > 0)) return false;
    if (action === "transfer" && (!toShopId || toShopId === shopId)) return false;
    if (needsReason && !reason) return false;
    return true;
  }, [productId, shopId, quantity, qty, action, toShopId, needsReason, reason]);

  const submit = async () => {
    if (!action || !valid) return;
    setSaving(true);
    const unitId = pack ? pack.unit.id : undefined;
    const chosenBatch = picksBatch && batch ? batch.id : undefined;
    const common = { productId: Number(productId), notes: notes.trim() || undefined };
    try {
      let res: StockActionResponse | null = null;
      if (action === "in") {
        await invApi.stockIn(businessId, {
          ...common, shopId: Number(shopId), quantity: qty, unitId,
          supplierId: supplierId ? Number(supplierId) : undefined,
          costPrice: costPrice ? Number(costPrice) : undefined,
          batchNumber: trackBatches && batchNumber.trim() ? batchNumber.trim() : undefined,
          expiryDate: trackExpiry && expiryDate ? expiryDate : undefined,
        });
      } else if (action === "out") {
        res = await invApi.stockOut(businessId, {
          ...common, shopId: Number(shopId), quantity: qty, unitId, reason, batchId: chosenBatch,
        });
      } else if (action === "transfer") {
        await invApi.transfer(businessId, {
          ...common, fromShopId: Number(shopId), toShopId: Number(toShopId), quantity: qty, unitId,
          batchId: chosenBatch,
        });
      } else {
        res = await invApi.adjust(businessId, {
          ...common, shopId: Number(shopId), newQuantity: qty, reason, batchId: chosenBatch,
          batchNumber: trackBatches && !chosenBatch && batchNumber.trim() ? batchNumber.trim() : undefined,
          expiryDate: trackExpiry && !chosenBatch && expiryDate ? expiryDate : undefined,
        });
      }
      if (res && isPending(res)) {
        toast.info("Sent to a manager for approval. Stock will change once it is approved.");
      } else {
        toast.success(`${TITLES[action]} saved`);
      }
      onDone();
      onClose();
    } catch (err) {
      toast.error(errorMessage(err, "Could not save"));
    } finally {
      setSaving(false);
    }
  };

  const today = new Date().toISOString().slice(0, 10);

  return (
    <Dialog open={action !== null} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader><DialogTitle>{action ? TITLES[action] : ""}</DialogTitle></DialogHeader>
        <div className="space-y-4">
          {needsApproval && needsReason && (
            <p className="rounded-md border border-yellow-200 bg-yellow-50 px-3 py-2 text-xs text-yellow-800">
              This will be sent to a manager for approval. Stock changes only after it is approved.
            </p>
          )}
          <div className="space-y-1.5">
            <Label>Product *</Label>
            <ProductPicker businessId={businessId} value={picked} onChange={chooseProduct} placeholder="Search name, SKU or barcode…" />
          </div>

          <div className={action === "transfer" ? "grid grid-cols-2 gap-3" : ""}>
            <div className="space-y-1.5">
              <Label>{action === "transfer" ? "From *" : "Location *"}</Label>
              <Select value={shopId} onValueChange={chooseShop}>
                <SelectTrigger><SelectValue placeholder="Select location" /></SelectTrigger>
                <SelectContent>
                  {shops.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.shopName}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            {action === "transfer" && (
              <div className="space-y-1.5">
                <Label>To *</Label>
                <Select value={toShopId} onValueChange={setToShopId}>
                  <SelectTrigger><SelectValue placeholder="Select location" /></SelectTrigger>
                  <SelectContent>
                    {(destinations ?? shops).filter((s) => String(s.id) !== shopId).map((s) => (
                      <SelectItem key={s.id} value={String(s.id)}>{s.shopName}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
          </div>

          {picksBatch && productId && shopId && (
            <div className="space-y-1.5">
              <Label>Batch</Label>
              <Select value={batchId} onValueChange={setBatchId}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={AUTO}>
                    {action === "adjust" ? "Whole location (all batches)"
                      : trackExpiry ? "Automatic: earliest expiry first" : "Automatic: oldest first"}
                  </SelectItem>
                  {batches.map((b) => (
                    <SelectItem key={b.id} value={String(b.id)}>
                      {b.batchNumber} · {fmtQty(b.quantityRemaining)} {baseName}
                      {b.expiryDate ? ` · exp ${formatDate(b.expiryDate)}` : ""}
                      {b.status === "EXPIRED" ? " (EXPIRED)" : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {batchId === AUTO && action !== "adjust" && (
                <p className="text-xs text-gray-500">
                  {fmtQty(usableNow)} {baseName} available{trackExpiry ? " (expired batches are skipped)" : ""}.
                </p>
              )}
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>
                {action === "adjust"
                  ? `Counted ${batch ? "in this batch" : "quantity"} (${baseName}) *`
                  : "Quantity *"}
              </Label>
              <Input type="number" min="0" step="any" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
            </div>
            {usesPacks && (
              <div className="space-y-1.5">
                <Label>Unit</Label>
                <Select value={unit} onValueChange={setUnit} disabled={packs.length === 0}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value={BASE}>{baseName}</SelectItem>
                    {packs.map((p) => (
                      <SelectItem key={p.unit.id} value={String(p.unit.id)}>
                        {p.unit.name} ({fmtQty(p.conversionFactor)} {baseName})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
          </div>
          {pack && qty > 0 && (
            <p className="-mt-2 text-xs text-gray-500">= {fmtQty(baseQty)} {baseName}</p>
          )}
          {action === "adjust" && (
            <p className="-mt-2 text-xs text-gray-500">
              Enter what is actually on the shelf. The difference is recorded as an adjustment
              {trackBatches && !batch ? "; any shortfall is taken from the oldest batches" : ""}.
            </p>
          )}

          {(action === "in" || (action === "adjust" && trackBatches && !batch)) && trackBatches && (
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Batch / lot no.</Label>
                <Input value={batchNumber} onChange={(e) => setBatchNumber(e.target.value)} placeholder="Auto if empty" />
              </div>
              {trackExpiry && (
                <div className="space-y-1.5">
                  <Label>Expiry date</Label>
                  <Input type="date" min={today} value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)} />
                </div>
              )}
            </div>
          )}

          {action === "in" && (
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Cost per {pack ? pack.unit.name.toLowerCase() : baseName.toLowerCase()}</Label>
                <Input type="number" min="0" step="0.01" value={costPrice} onChange={(e) => setCostPrice(e.target.value)}
                  placeholder="Product cost" />
              </div>
              {suppliers.length > 0 && (
                <div className="space-y-1.5">
                  <Label>Supplier</Label>
                  <Select value={supplierId} onValueChange={setSupplierId}>
                    <SelectTrigger><SelectValue placeholder="Optional" /></SelectTrigger>
                    <SelectContent>
                      {suppliers.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.companyName}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>
          )}

          {needsReason && (
            <div className="space-y-1.5">
              <Label>Reason *</Label>
              <Select value={reason} onValueChange={setReason}>
                <SelectTrigger><SelectValue placeholder="Why is stock changing?" /></SelectTrigger>
                <SelectContent>
                  {reasons.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
          )}

          <div className="space-y-1.5">
            <Label>Notes</Label>
            <Input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Optional" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={submit} disabled={!valid || saving}>
            {saving ? "Saving…" : needsApproval && needsReason ? "Send for approval" : action ? TITLES[action] : "Save"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
