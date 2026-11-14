"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Plus, Trash2, ShoppingCart } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { sales as salesApi, customers as customersApi, shops as shopsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { formatCurrency, errorMessage } from "@/lib/utils";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { useProductSearch } from "@/hooks/useProductSearch";
import { CartLineOptions, addProduct, type CartLine } from "@/components/sales/CartLineOptions";
import { PAYMENT_METHODS } from "@/constants/Strings";
import type { Product, Customer, Shop, SaleItemPayload, PaymentMethod } from "@/Types";

export default function NewSalePage() {
  const router = useRouter();
  const { activeBusiness } = useBusiness();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [shops, setShops] = useState<Shop[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const { settings } = useBusinessSettings();
  const multipleUnits = Boolean(settings?.multipleUnits);
  const trackBatches = Boolean(settings?.trackBatches);
  const trackExpiry = Boolean(settings?.trackExpiry);
  const [shopId, setShopId] = useState<number | null>(null);
  const [customerId, setCustomerId] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");
  const [discount, setDiscount] = useState(0);
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [search, setSearch] = useState("");

  useEffect(() => {
    if (!activeBusiness) return;
    const id = activeBusiness.businessId;
    Promise.all([
      customersApi.list(id, { size: 200 }),
      shopsApi.list(id, { mine: true }),
    ]).then(([c, s]) => {
      setCustomers(c.content);
      setShops(s.content);
      if (s.content[0]) setShopId(s.content[0].id);
    }).catch(() => {});
  }, [activeBusiness]);

  const addToCart = (product: Product) => setCart((prev) => addProduct(prev, product));

  const updateQty = (key: string, qty: number) => {
    if (qty <= 0) setCart((prev) => prev.filter((i) => i.key !== key));
    else setCart((prev) => prev.map((i) => i.key === key ? { ...i, quantity: qty } : i));
  };

  const replaceLine = (line: CartLine) => setCart((prev) => prev.map((i) => i.key === line.key ? line : i));

  const subtotal = cart.reduce((acc, i) => acc + i.quantity * i.unitPrice, 0);
  const total = Math.max(0, subtotal - discount);

  // Searched on the server (name, SKU or barcode), so any catalogue size works.
  const { results: filtered } = useProductSearch(activeBusiness?.businessId, search, 50);

  const handleSubmit = async () => {
    if (!activeBusiness || !shopId) {
      toast.error("Select a shop first");
      return;
    }
    if (cart.length === 0) {
      toast.error("Add at least one item");
      return;
    }
    setSubmitting(true);
    try {
      const items: SaleItemPayload[] = cart.map((i) => ({
        productId: i.product.id,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        unitId: i.unitId,
        batchId: i.batchId,
      }));
      await salesApi.create(activeBusiness.businessId, {
        shopId,
        customerId: customerId ?? undefined,
        paymentMethod,
        items,
        discountAmount: discount || undefined,
        notes: notes || undefined,
      });
      toast.success("Sale recorded");
      router.push("/sales");
    } catch (err) {
      toast.error(errorMessage(err, "Failed to create sale"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader title="New Sale" description="Record a new sales transaction" />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Product catalog */}
        <div className="lg:col-span-2 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Select Products</CardTitle>
              <Input
                placeholder="Search by name or SKU…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="mt-2"
              />
            </CardHeader>
            <CardContent className="p-0">
              <div className="max-h-96 overflow-y-auto divide-y divide-gray-100">
                {filtered.map((p) => (
                  <button
                    key={p.id}
                    onClick={() => addToCart(p)}
                    className="flex w-full items-center justify-between px-4 py-3 hover:bg-blue-50 transition-colors text-left"
                  >
                    <div>
                      <p className="text-sm font-medium">{p.productName}</p>
                      <p className="text-xs text-gray-400">{p.sku ? `SKU: ${p.sku}` : ""} · Stock: {p.totalCount}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-sm font-semibold text-blue-600">{formatCurrency(p.sellingPrice)}</p>
                      <Plus className="h-4 w-4 text-blue-400 ml-auto" />
                    </div>
                  </button>
                ))}
                {filtered.length === 0 && (
                  <p className="px-4 py-6 text-sm text-gray-400 text-center">No products found</p>
                )}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Cart */}
        <div className="space-y-4">
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <ShoppingCart className="h-5 w-5 text-gray-500" />
                <CardTitle className="text-base">Cart ({cart.length})</CardTitle>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              {cart.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-4">No items added</p>
              ) : (
                <>
                  {cart.map((item) => (
                    <div key={item.key} className="flex items-center gap-2">
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-medium truncate">{item.product.productName}</p>
                        <p className="text-xs text-gray-400">{formatCurrency(item.unitPrice)} per {item.unitName ?? item.product.unit?.name ?? "unit"}</p>
                        {activeBusiness && (
                          <CartLineOptions
                            businessId={activeBusiness.businessId} shopId={shopId} line={item} onChange={replaceLine} compact
                            multipleUnits={multipleUnits} trackBatches={trackBatches} trackExpiry={trackExpiry}
                          />
                        )}
                      </div>
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          onClick={() => updateQty(item.key, item.quantity - 1)}
                          className="h-6 w-6 rounded border border-gray-300 flex items-center justify-center text-sm hover:bg-gray-50"
                        >−</button>
                        <input
                          type="number"
                          className="w-10 text-center text-sm border border-gray-300 rounded h-6 p-0"
                          value={item.quantity}
                          onChange={(e) => updateQty(item.key, Number(e.target.value))}
                        />
                        <button
                          onClick={() => updateQty(item.key, item.quantity + 1)}
                          className="h-6 w-6 rounded border border-gray-300 flex items-center justify-center text-sm hover:bg-gray-50"
                        >+</button>
                      </div>
                      <p className="text-xs font-semibold w-16 text-right">
                        {formatCurrency(item.quantity * item.unitPrice)}
                      </p>
                      <button onClick={() => updateQty(item.key, 0)} className="text-red-400 hover:text-red-600">
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  ))}

                  <Separator />
                  <div className="space-y-1 text-sm">
                    <div className="flex justify-between">
                      <span className="text-gray-500">Subtotal</span>
                      <span>{formatCurrency(subtotal)}</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-gray-500">Discount</span>
                      <Input
                        type="number" min="0" step="0.01"
                        value={discount}
                        onChange={(e) => setDiscount(Number(e.target.value))}
                        className="w-24 h-7 text-xs text-right"
                      />
                    </div>
                    <div className="flex justify-between font-semibold text-base">
                      <span>Total</span>
                      <span className="text-blue-600">{formatCurrency(total)}</span>
                    </div>
                  </div>
                </>
              )}

              <Separator />

              <div className="space-y-3">
                <div className="space-y-1.5">
                  <Label className="text-xs">Shop *</Label>
                  <Select
                    value={shopId ? String(shopId) : ""}
                    onValueChange={(v) => setShopId(Number(v))}
                  >
                    <SelectTrigger className="h-8 text-xs"><SelectValue placeholder="Select shop" /></SelectTrigger>
                    <SelectContent>
                      {shops.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.shopName}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs">Customer (optional)</Label>
                  <Select onValueChange={(v) => setCustomerId(Number(v))}>
                    <SelectTrigger className="h-8 text-xs"><SelectValue placeholder="Walk-in customer" /></SelectTrigger>
                    <SelectContent>
                      {customers.map((c) => (
                        <SelectItem key={c.id} value={String(c.id)}>
                          {c.firstName} {c.lastName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs">Payment method *</Label>
                  <Select
                    value={paymentMethod}
                    onValueChange={(v) => setPaymentMethod(v as PaymentMethod)}
                  >
                    <SelectTrigger className="h-8 text-xs"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {PAYMENT_METHODS.map((m) => (
                        <SelectItem key={m.value} value={m.value}>{m.label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs">Notes</Label>
                  <Input
                    className="h-8 text-xs" value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    placeholder="Optional notes…"
                  />
                </div>
              </div>

              <Button
                className="w-full"
                onClick={handleSubmit}
                disabled={submitting || cart.length === 0}
              >
                {submitting ? "Processing…" : `Complete Sale · ${formatCurrency(total)}`}
              </Button>
              <Button variant="outline" className="w-full" onClick={() => router.back()}>
                Cancel
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
