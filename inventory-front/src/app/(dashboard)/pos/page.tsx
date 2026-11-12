"use client";
import { useState, useEffect, useMemo } from "react";
import { toast } from "sonner";
import { ShoppingCart, Trash2, Plus, Minus, CreditCard, Banknote, X } from "lucide-react";
import { products as productsApi, shops as shopsApi, sales as salesApi } from "@/lib/api";
import { useBusiness } from "@/context/BusinessContext";
import type { Product, Shop, SalePayload, PaymentMethod } from "@/Types";
import { cn, formatCurrency } from "@/lib/utils";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { CartLineOptions, addProduct, type CartLine } from "@/components/sales/CartLineOptions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const PAYMENT_OPTS: { label: string; value: PaymentMethod; icon: React.ReactNode }[] = [
  { label: "Cash",          value: "CASH",          icon: <Banknote className="h-4 w-4" /> },
  { label: "Card",          value: "BANK_TRANSFER",  icon: <CreditCard className="h-4 w-4" /> },
  { label: "Mobile Money",  value: "MOBILE_MONEY",   icon: <span className="text-xs font-bold">MoMo</span> },
];

export default function POSPage() {
  const { activeBusiness } = useBusiness();
  const bizId = activeBusiness?.businessId ?? 0;

  const [allProducts, setAllProducts] = useState<Product[]>([]);
  const [shops, setShops] = useState<Shop[]>([]);
  const [shopId, setShopId] = useState<number | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<string>("All");
  const [search, setSearch] = useState("");
  const [cart, setCart] = useState<CartLine[]>([]);
  const { settings } = useBusinessSettings();
  const multipleUnits = Boolean(settings?.multipleUnits);
  const trackBatches = Boolean(settings?.trackBatches);
  const trackExpiry = Boolean(settings?.trackExpiry);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");
  const [cashTendered, setCashTendered] = useState("");
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!bizId) return;
    setLoading(true);
    Promise.all([
      productsApi.list(bizId, { size: 200 }),
      shopsApi.list(bizId, { mine: true }),
    ]).then(([p, s]) => {
      setAllProducts(p.content);
      const shopList = s.content ?? (s as any);
      setShops(Array.isArray(shopList) ? shopList : []);
      if (Array.isArray(shopList) && shopList.length > 0) setShopId(shopList[0].id);
    }).catch(() => toast.error("Failed to load products"))
      .finally(() => setLoading(false));
  }, [bizId]);

  const categories = useMemo(() => {
    const names = Array.from(new Set(allProducts.map((p) => p.category?.categoryName ?? "Uncategorised")));
    return ["All", ...names];
  }, [allProducts]);

  const filtered = useMemo(() => allProducts.filter((p) => {
    const matchCat = categoryFilter === "All" || p.category?.categoryName === categoryFilter;
    const matchQ = !search || p.productName.toLowerCase().includes(search.toLowerCase()) || (p.sku ?? "").toLowerCase().includes(search.toLowerCase());
    return matchCat && matchQ;
  }), [allProducts, categoryFilter, search]);

  const addToCart = (product: Product) => setCart((prev) => addProduct(prev, product));

  const updateQty = (key: string, delta: number) => {
    setCart((prev) => prev.map((i) => i.key === key ? { ...i, quantity: Math.max(1, i.quantity + delta) } : i));
  };

  const replaceLine = (line: CartLine) => setCart((prev) => prev.map((i) => i.key === line.key ? line : i));

  const removeItem = (key: string) => setCart((prev) => prev.filter((i) => i.key !== key));

  const subtotal = cart.reduce((s, i) => s + i.unitPrice * i.quantity, 0);
  const tendered = parseFloat(cashTendered) || 0;
  const change = paymentMethod === "CASH" ? Math.max(0, tendered - subtotal) : 0;
  const canCheckout = cart.length > 0 && shopId != null && (paymentMethod !== "CASH" || tendered >= subtotal);

  const checkout = async () => {
    if (!shopId) return toast.error("Select a shop first");
    setSubmitting(true);
    const payload: SalePayload = {
      shopId,
      paymentMethod,
      items: cart.map((i) => ({
        productId: i.product.id, quantity: i.quantity, unitPrice: i.unitPrice,
        unitId: i.unitId, batchId: i.batchId,
      })),
    };
    try {
      const invoice = await salesApi.create(bizId, payload);
      toast.success(`Sale complete — ${invoice.invoiceNumber}`);
      setCart([]);
      setCashTendered("");
    } catch (err: any) {
      toast.error(err?.message ?? "Sale failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex h-full min-h-[34rem] flex-col overflow-hidden rounded-4xl border border-line/80 bg-surface shadow-card lg:flex-row">
      {/* ── Product Grid ──────────────────────────────────────── */}
      <div className="flex min-h-[20rem] flex-1 flex-col overflow-hidden border-b border-line lg:border-b-0 lg:border-r">
        {/* Top bar */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 bg-white shrink-0">
          <Input
            placeholder="Search product or SKU…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="max-w-xs"
          />
          {shops.length > 1 && (
            <select
              className="text-sm border border-gray-200 rounded-md px-3 py-2 bg-white"
              value={shopId ?? ""}
              onChange={(e) => setShopId(Number(e.target.value))}
            >
              {shops.map((s) => <option key={s.id} value={s.id}>{s.shopName}</option>)}
            </select>
          )}
        </div>

        {/* Category tabs */}
        <div className="flex gap-1.5 px-4 py-2 border-b border-gray-100 bg-gray-50 overflow-x-auto shrink-0">
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setCategoryFilter(cat)}
              className={cn(
                "shrink-0 rounded-full px-4 py-1.5 text-sm font-medium transition-colors",
                categoryFilter === cat
                  ? "bg-blue-600 text-white"
                  : "bg-white text-gray-600 border border-gray-200 hover:bg-gray-100"
              )}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Products */}
        <div className="flex-1 overflow-y-auto p-4">
          {loading ? (
            <div className="flex items-center justify-center h-40 text-gray-400">Loading…</div>
          ) : filtered.length === 0 ? (
            <div className="flex items-center justify-center h-40 text-gray-400">No products found</div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
              {filtered.map((product) => (
                <button
                  key={product.id}
                  onClick={() => addToCart(product)}
                  className="flex flex-col items-start rounded-3xl border border-line/80 bg-surface shadow-card p-3 text-left shadow-sm hover:shadow-md hover:border-blue-300 hover:-translate-y-0.5 transition-all active:scale-95"
                >
                  <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-600 text-xs font-bold">
                    {product.productName.slice(0, 2).toUpperCase()}
                  </div>
                  <p className="text-sm font-semibold text-gray-900 leading-tight line-clamp-2">
                    {product.productName}
                  </p>
                  {product.sku && <p className="text-xs text-gray-400 mt-0.5">{product.sku}</p>}
                  <p className="mt-2 text-base font-bold text-blue-600">{formatCurrency(product.sellingPrice)}</p>
                  <p className={cn("text-xs mt-0.5", product.totalCount <= 0 ? "text-red-500" : "text-gray-400")}>
                    {product.totalCount <= 0 ? "Out of stock" : `${product.totalCount} in stock`}
                  </p>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ── Cart ──────────────────────────────────────────────── */}
      <div className="flex w-full shrink-0 flex-col bg-surface lg:w-96">
        {/* Cart header */}
        <div className="flex items-center gap-2 px-4 py-3 border-b border-gray-200">
          <ShoppingCart className="h-5 w-5 text-blue-600" />
          <span className="font-semibold text-gray-900">Cart</span>
          {cart.length > 0 && (
            <span className="ml-auto text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full font-medium">
              {cart.length} items
            </span>
          )}
          {cart.length > 0 && (
            <button onClick={() => setCart([])} className="text-gray-400 hover:text-red-500 transition-colors">
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        {/* Cart items */}
        <div className="flex-1 overflow-y-auto divide-y divide-gray-100">
          {cart.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-40 text-gray-400 gap-2">
              <ShoppingCart className="h-10 w-10 opacity-30" />
              <p className="text-sm">Cart is empty</p>
            </div>
          ) : (
            cart.map((item) => (
              <div key={item.key} className="flex items-center gap-3 px-4 py-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{item.product.productName}</p>
                  <p className="text-xs text-gray-500">{formatCurrency(item.unitPrice)} per {item.unitName ?? item.product.unit?.name ?? "unit"}</p>
                  <CartLineOptions
                    businessId={bizId} shopId={shopId} line={item} onChange={replaceLine} compact
                    multipleUnits={multipleUnits} trackBatches={trackBatches} trackExpiry={trackExpiry}
                  />
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    onClick={() => updateQty(item.key, -1)}
                    className="flex h-6 w-6 items-center justify-center rounded-md border border-gray-200 hover:bg-gray-100"
                  >
                    <Minus className="h-3 w-3" />
                  </button>
                  <span className="w-6 text-center text-sm font-medium">{item.quantity}</span>
                  <button
                    onClick={() => updateQty(item.key, 1)}
                    className="flex h-6 w-6 items-center justify-center rounded-md border border-gray-200 hover:bg-gray-100"
                  >
                    <Plus className="h-3 w-3" />
                  </button>
                </div>
                <div className="w-16 text-right shrink-0">
                  <p className="text-sm font-semibold">{formatCurrency(item.unitPrice * item.quantity)}</p>
                </div>
                <button onClick={() => removeItem(item.key)} className="text-gray-300 hover:text-red-500 transition-colors">
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))
          )}
        </div>

        {/* Checkout panel */}
        <div className="border-t border-gray-200 p-4 space-y-4 shrink-0">
          {/* Subtotal */}
          <div className="flex justify-between text-lg font-bold">
            <span>Total</span>
            <span className="text-blue-600">{formatCurrency(subtotal)}</span>
          </div>

          {/* Payment method */}
          <div className="grid grid-cols-3 gap-2">
            {PAYMENT_OPTS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => setPaymentMethod(opt.value)}
                className={cn(
                  "flex flex-col items-center gap-1 rounded-lg border py-2.5 text-xs font-medium transition-colors",
                  paymentMethod === opt.value
                    ? "border-blue-500 bg-blue-50 text-blue-700"
                    : "border-gray-200 text-gray-600 hover:bg-gray-50"
                )}
              >
                {opt.icon}
                {opt.label}
              </button>
            ))}
          </div>

          {/* Cash tendered */}
          {paymentMethod === "CASH" && (
            <div className="space-y-2">
              <div className="flex gap-2">
                <Input
                  type="number"
                  placeholder="Cash tendered"
                  value={cashTendered}
                  onChange={(e) => setCashTendered(e.target.value)}
                  className="flex-1"
                />
              </div>
              {/* Quick amounts */}
              <div className="grid grid-cols-4 gap-1.5">
                {[subtotal, Math.ceil(subtotal / 10) * 10, Math.ceil(subtotal / 50) * 50, Math.ceil(subtotal / 100) * 100]
                  .filter((v, i, a) => a.indexOf(v) === i)
                  .slice(0, 4)
                  .map((amt) => (
                    <button
                      key={amt}
                      onClick={() => setCashTendered(String(amt))}
                      className="rounded-md border border-gray-200 py-1.5 text-xs font-medium hover:bg-gray-50"
                    >
                      {formatCurrency(amt)}
                    </button>
                  ))}
              </div>
              {tendered > 0 && (
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">Change</span>
                  <span className={cn("font-semibold", change >= 0 ? "text-green-600" : "text-red-500")}>
                    {formatCurrency(change)}
                  </span>
                </div>
              )}
            </div>
          )}

          <Button
            className="w-full h-12 text-base"
            disabled={!canCheckout || submitting}
            onClick={checkout}
          >
            {submitting ? "Processing…" : "Complete Sale"}
          </Button>
        </div>
      </div>
    </div>
  );
}
