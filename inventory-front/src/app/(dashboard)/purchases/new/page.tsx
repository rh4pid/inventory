"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { purchases as purchasesApi, suppliers as suppliersApi, shops as shopsApi, units as unitsApi } from "@/lib/api";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { ProductPicker } from "@/components/shared/ProductPicker";
import { fmtQty } from "@/constants/Inventory";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { formatCurrency, errorMessage } from "@/lib/utils";
import { PAYMENT_METHODS } from "@/constants/Strings";
import type { Product, ProductUnit, Supplier, Shop, PurchaseItemPayload, PaymentMethod } from "@/Types";

/** unitId "" = the product's base unit; otherwise one of its pack sizes. */
interface LineItem {
  product: Product; quantity: number; unitPrice: number; unitId: string; packs: ProductUnit[];
  /** Lot details, used when the business tracks batches / expiry. */
  batchNumber: string; expiryDate: string;
}

export default function NewPurchasePage() {
  const router = useRouter();
  const { activeBusiness } = useBusiness();
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [shops, setShops] = useState<Shop[]>([]);
  const [lines, setLines] = useState<LineItem[]>([]);
  const [shopId, setShopId] = useState<number | null>(null);
  const [supplierId, setSupplierId] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const { settings } = useBusinessSettings();

  useEffect(() => {
    if (!activeBusiness) return;
    const id = activeBusiness.businessId;
    Promise.all([
      suppliersApi.list(id, { size: 200 }),
      shopsApi.list(id, { mine: true }),
    ]).then(([s, sh]) => {
      setSuppliers(s.content);
      setShops(sh.content);
      if (sh.content[0]) setShopId(sh.content[0].id);
    }).catch(() => {});
  }, [activeBusiness]);

  // Picked from a server-side search, so any size of catalogue works.
  const addLine = async (product: Product | null) => {
    if (!product || !activeBusiness) return;
    if (lines.find((l) => l.product.id === product.id)) return;
    let packs: ProductUnit[] = [];
    if (settings?.multipleUnits) {
      packs = await unitsApi.forProduct(activeBusiness.businessId, product.id).catch(() => []);
    }
    setLines((prev) => [...prev, {
      product, quantity: 1, unitPrice: product.costPrice ?? 0, unitId: "", packs, batchNumber: "", expiryDate: "",
    }]);
  };

  const today = new Date().toISOString().slice(0, 10);

  const updateLineText = (productId: number, field: "batchNumber" | "expiryDate", value: string) => {
    setLines((prev) => prev.map((l) => l.product.id === productId ? { ...l, [field]: value } : l));
  };

  const updateLine = (productId: number, field: "quantity" | "unitPrice", value: number) => {
    setLines((prev) => prev.map((l) => l.product.id === productId ? { ...l, [field]: value } : l));
  };

  /** Switching unit re-prices the line at base cost × pack size; the user can still edit it. */
  const changeUnit = (productId: number, unitId: string) => {
    setLines((prev) => prev.map((l) => {
      if (l.product.id !== productId) return l;
      const pack = l.packs.find((p) => String(p.unit.id) === unitId);
      const factor = pack?.conversionFactor ?? 1;
      return { ...l, unitId: pack ? unitId : "", unitPrice: Math.round((l.product.costPrice ?? 0) * factor * 100) / 100 };
    }));
  };

  const removeLine = (productId: number) => {
    setLines((prev) => prev.filter((l) => l.product.id !== productId));
  };

  const total = lines.reduce((acc, l) => acc + l.quantity * l.unitPrice, 0);

  const handleSubmit = async () => {
    if (!activeBusiness || !shopId) {
      toast.error("Select a shop first");
      return;
    }
    if (lines.length === 0) {
      toast.error("Add at least one item");
      return;
    }
    setSubmitting(true);
    try {
      const items: PurchaseItemPayload[] = lines.map((l) => ({
        productId: l.product.id,
        quantity: l.quantity,
        unitId: l.unitId ? Number(l.unitId) : undefined,
        unitPrice: l.unitPrice,
        batchNumber: settings?.trackBatches && l.batchNumber.trim() ? l.batchNumber.trim() : undefined,
        expiryDate: settings?.trackExpiry && l.expiryDate ? l.expiryDate : undefined,
      }));
      await purchasesApi.create(activeBusiness.businessId, {
        shopId,
        supplierId: supplierId ?? undefined,
        paymentMethod,
        items,
        notes: notes || undefined,
      });
      toast.success("Purchase recorded");
      router.push("/purchases");
    } catch (err) {
      toast.error(errorMessage(err, "Failed to create purchase"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-3xl">
      <PageHeader title="New Purchase" description="Record a supplier purchase order" />

      <div className="space-y-6">
        <Card>
          <CardHeader><CardTitle className="text-base">Purchase Details</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Shop *</Label>
                <Select value={shopId ? String(shopId) : ""} onValueChange={(v) => setShopId(Number(v))}>
                  <SelectTrigger><SelectValue placeholder="Select shop" /></SelectTrigger>
                  <SelectContent>
                    {shops.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.shopName}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label>Supplier (optional)</Label>
                <Select onValueChange={(v) => setSupplierId(Number(v))}>
                  <SelectTrigger><SelectValue placeholder="Select supplier" /></SelectTrigger>
                  <SelectContent>
                    {suppliers.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.companyName}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Payment method *</Label>
                <Select value={paymentMethod} onValueChange={(v) => setPaymentMethod(v as PaymentMethod)}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {PAYMENT_METHODS.map((m) => <SelectItem key={m.value} value={m.value}>{m.label}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label>Notes</Label>
                <Input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Optional…" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Line Items</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            {activeBusiness && (
              <ProductPicker businessId={activeBusiness.businessId} value={null} onChange={addLine} clearOnPick
                placeholder="Add a product: type its name, SKU or scan a barcode…" />
            )}

            {lines.length > 0 && (
              <div className="space-y-2">
                <div className="grid grid-cols-12 gap-2 text-xs font-medium text-gray-500 px-1">
                  <div className="col-span-4">Product</div>
                  <div className="col-span-3">Unit</div>
                  <div className="col-span-2">Quantity</div>
                  <div className="col-span-2">Cost / unit</div>
                  <div className="col-span-1"></div>
                </div>
                {lines.map((line) => {
                  const baseName = line.product.unit?.name ?? "Base unit";
                  const pack = line.packs.find((p) => String(p.unit.id) === line.unitId);
                  return (
                  <div key={line.product.id} className="grid grid-cols-12 gap-2 items-center">
                    <div className="col-span-4">
                      <p className="text-sm font-medium">{line.product.productName}</p>
                      <p className="text-xs text-gray-400">
                        {formatCurrency(line.quantity * line.unitPrice)}
                        {pack && ` · ${fmtQty(line.quantity * pack.conversionFactor)} ${baseName}`}
                      </p>
                    </div>
                    <div className="col-span-3">
                      <Select value={line.unitId || "base"} onValueChange={(v) => changeUnit(line.product.id, v)}
                        disabled={line.packs.length === 0}>
                        <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value="base">{baseName}</SelectItem>
                          {line.packs.map((p) => (
                            <SelectItem key={p.unit.id} value={String(p.unit.id)}>
                              {p.unit.name} ({fmtQty(p.conversionFactor)})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="col-span-2">
                      <Input
                        type="number" min="0.01" step="any"
                        value={line.quantity}
                        onChange={(e) => updateLine(line.product.id, "quantity", Number(e.target.value))}
                        className="h-8 text-sm"
                      />
                    </div>
                    <div className="col-span-2">
                      <Input
                        type="number" min="0" step="0.01"
                        aria-label={`Cost per ${pack ? pack.unit.name : baseName}`}
                        value={line.unitPrice}
                        onChange={(e) => updateLine(line.product.id, "unitPrice", Number(e.target.value))}
                        className="h-8 text-sm"
                      />
                    </div>
                    <div className="col-span-1 flex justify-center">
                      <button onClick={() => removeLine(line.product.id)} className="text-red-400 hover:text-red-600"
                        aria-label={`Remove ${line.product.productName}`}>
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                    {settings?.trackBatches && (
                      <>
                        <div className="col-span-4" />
                        <div className="col-span-3">
                          <Input
                            placeholder="Batch / lot no. (auto)"
                            aria-label={`Batch number for ${line.product.productName}`}
                            value={line.batchNumber}
                            onChange={(e) => updateLineText(line.product.id, "batchNumber", e.target.value)}
                            className="h-8 text-sm"
                          />
                        </div>
                        {settings.trackExpiry && (
                          <div className="col-span-4">
                            <Input
                              type="date" min={today}
                              aria-label={`Expiry date for ${line.product.productName}`}
                              value={line.expiryDate}
                              onChange={(e) => updateLineText(line.product.id, "expiryDate", e.target.value)}
                              className="h-8 text-sm"
                            />
                          </div>
                        )}
                      </>
                    )}
                  </div>
                  );
                })}

                <Separator />
                <div className="flex justify-between items-center font-semibold">
                  <span>Total</span>
                  <span className="text-blue-600 text-lg">{formatCurrency(total)}</span>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <div className="flex gap-3">
          <Button type="button" variant="outline" onClick={() => router.back()}>Cancel</Button>
          <Button onClick={handleSubmit} disabled={submitting || lines.length === 0}>
            {submitting ? "Saving…" : "Create Purchase"}
          </Button>
        </div>
      </div>
    </div>
  );
}
