"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { purchases as purchasesApi, products as productsApi, suppliers as suppliersApi, shops as shopsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { formatCurrency } from "@/lib/utils";
import { PAYMENT_METHODS } from "@/constants/Strings";
import type { Product, Supplier, Shop, PurchaseItemPayload, PaymentMethod } from "@/Types";

interface LineItem { product: Product; quantity: number; unitPrice: number; }

export default function NewPurchasePage() {
  const router = useRouter();
  const { activeBusiness } = useBusiness();
  const [prods, setProds] = useState<Product[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [shops, setShops] = useState<Shop[]>([]);
  const [lines, setLines] = useState<LineItem[]>([]);
  const [shopId, setShopId] = useState<number | null>(null);
  const [supplierId, setSupplierId] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [selProduct, setSelProduct] = useState<string>("");

  useEffect(() => {
    if (!activeBusiness) return;
    const id = activeBusiness.businessId;
    Promise.all([
      productsApi.list(id, { size: 100 }),
      suppliersApi.list(id, { size: 100 }),
      shopsApi.list(id),
    ]).then(([p, s, sh]) => {
      setProds(p.content);
      setSuppliers(s.content);
      setShops(sh.content);
      if (sh.content[0]) setShopId(sh.content[0].id);
    }).catch(() => {});
  }, [activeBusiness]);

  const addLine = () => {
    if (!selProduct) return;
    const product = prods.find((p) => String(p.id) === selProduct);
    if (!product) return;
    if (lines.find((l) => l.product.id === product.id)) return;
    setLines((prev) => [...prev, { product, quantity: 1, unitPrice: product.costPrice }]);
    setSelProduct("");
  };

  const updateLine = (productId: number, field: "quantity" | "unitPrice", value: number) => {
    setLines((prev) => prev.map((l) => l.product.id === productId ? { ...l, [field]: value } : l));
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
        unitPrice: l.unitPrice,
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
    } catch (err: any) {
      toast.error(err.message ?? "Failed to create purchase");
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
            <div className="flex gap-2">
              <Select value={selProduct} onValueChange={setSelProduct}>
                <SelectTrigger className="flex-1"><SelectValue placeholder="Select product to add" /></SelectTrigger>
                <SelectContent>
                  {prods.map((p) => <SelectItem key={p.id} value={String(p.id)}>{p.productName}</SelectItem>)}
                </SelectContent>
              </Select>
              <Button type="button" onClick={addLine} disabled={!selProduct}>
                <Plus className="h-4 w-4" /> Add
              </Button>
            </div>

            {lines.length > 0 && (
              <div className="space-y-2">
                <div className="grid grid-cols-12 gap-2 text-xs font-medium text-gray-500 px-1">
                  <div className="col-span-5">Product</div>
                  <div className="col-span-3">Quantity</div>
                  <div className="col-span-3">Unit Cost</div>
                  <div className="col-span-1"></div>
                </div>
                {lines.map((line) => (
                  <div key={line.product.id} className="grid grid-cols-12 gap-2 items-center">
                    <div className="col-span-5">
                      <p className="text-sm font-medium">{line.product.productName}</p>
                      <p className="text-xs text-gray-400">{formatCurrency(line.quantity * line.unitPrice)}</p>
                    </div>
                    <div className="col-span-3">
                      <Input
                        type="number" min="0.01" step="any"
                        value={line.quantity}
                        onChange={(e) => updateLine(line.product.id, "quantity", Number(e.target.value))}
                        className="h-8 text-sm"
                      />
                    </div>
                    <div className="col-span-3">
                      <Input
                        type="number" min="0" step="0.01"
                        value={line.unitPrice}
                        onChange={(e) => updateLine(line.product.id, "unitPrice", Number(e.target.value))}
                        className="h-8 text-sm"
                      />
                    </div>
                    <div className="col-span-1 flex justify-center">
                      <button onClick={() => removeLine(line.product.id)} className="text-red-400 hover:text-red-600">
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}

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
