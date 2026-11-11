"use client";
import { useEffect, useState, useCallback } from "react";
import { AlertTriangle, ArrowDownToLine, ArrowUpFromLine, ArrowRightLeft, SlidersHorizontal } from "lucide-react";
import { toast } from "sonner";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useBusiness } from "@/context/BusinessContext";
import { inventory as invApi, products as productsApi, suppliers as suppliersApi, shops as shopsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import type { Inventory, Product, Supplier, Shop } from "@/Types";

type ModalType = "in" | "out" | "transfer" | "adjust" | null;

const stockInSchema = z.object({
  productId: z.coerce.number().min(1, "Required"),
  shopId: z.coerce.number().min(1, "Required"),
  quantity: z.coerce.number().positive("Must be positive"),
  costPrice: z.coerce.number().min(0).optional(),
  supplierId: z.coerce.number().optional(),
  notes: z.string().optional(),
});

export default function InventoryPage() {
  const { activeBusiness } = useBusiness();
  const [items, setItems] = useState<Inventory[]>([]);
  const [lowStock, setLowStock] = useState<Inventory[]>([]);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState<ModalType>(null);
  const [prods, setProds] = useState<Product[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [shops, setShops] = useState<Shop[]>([]);
  const [activeShop, setActiveShop] = useState<string>("all");

  const { register, handleSubmit, setValue, reset, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(stockInSchema),
  });

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    const id = activeBusiness.businessId;
    setLoading(true);
    try {
      const [low, shopRes, prodRes, supRes] = await Promise.all([
        invApi.lowStock(id),
        shopsApi.list(id),
        productsApi.list(id, { size: 100 }),
        suppliersApi.list(id, { size: 100 }),
      ]);
      setLowStock(low);
      setShops(shopRes.content);
      setProds(prodRes.content);
      setSuppliers(supRes.content);

      if (shopRes.content.length > 0) {
        const first = shopRes.content[0];
        const inv = await invApi.listByShop(id, first.id, { size: 100 });
        setItems(inv.content);
        setActiveShop(String(first.id));
      }
    } catch (err: any) { toast.error(err.message); }
    setLoading(false);
  }, [activeBusiness]);

  useEffect(() => { load(); }, [load]);

  const handleTabChange = async (shopId: string) => {
    if (!activeBusiness || shopId === "all") return;
    setActiveShop(shopId);
    try {
      const inv = await invApi.listByShop(activeBusiness.businessId, Number(shopId), { size: 100 });
      setItems(inv.content);
    } catch {}
  };

  const onStockIn = async (data: any) => {
    if (!activeBusiness) return;
    try {
      await invApi.stockIn(activeBusiness.businessId, data);
      toast.success("Stock added");
      reset(); setModal(null); load();
    } catch (err: any) { toast.error(err.message); }
  };

  if (loading && items.length === 0) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Inventory"
        description="Track stock levels across all shops"
        action={
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => setModal("in")}>
              <ArrowDownToLine className="h-4 w-4" /> Stock In
            </Button>
            <Button variant="outline" size="sm" onClick={() => setModal("out")}>
              <ArrowUpFromLine className="h-4 w-4" /> Stock Out
            </Button>
            <Button variant="outline" size="sm" onClick={() => setModal("transfer")}>
              <ArrowRightLeft className="h-4 w-4" /> Transfer
            </Button>
            <Button variant="outline" size="sm" onClick={() => setModal("adjust")}>
              <SlidersHorizontal className="h-4 w-4" /> Adjust
            </Button>
          </div>
        }
      />

      {/* Low stock banner */}
      {lowStock.length > 0 && (
        <div className="mb-4 flex items-center gap-3 rounded-lg border border-yellow-200 bg-yellow-50 px-4 py-3">
          <AlertTriangle className="h-5 w-5 text-yellow-600 shrink-0" />
          <p className="text-sm text-yellow-800">
            <strong>{lowStock.length}</strong> product{lowStock.length > 1 ? "s" : ""} running low on stock.
          </p>
        </div>
      )}

      {shops.length === 0 ? (
        <EmptyState title="No shops configured" description="Add a shop to your business first." />
      ) : (
        <Tabs value={activeShop} onValueChange={handleTabChange}>
          <TabsList className="mb-4">
            {shops.map((s) => (
              <TabsTrigger key={s.id} value={String(s.id)}>{s.shopName}</TabsTrigger>
            ))}
          </TabsList>

          {shops.map((s) => (
            <TabsContent key={s.id} value={String(s.id)}>
              {items.length === 0 ? (
                <EmptyState title="No inventory" description="Stock in products to this shop." />
              ) : (
                <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Product</TableHead>
                        <TableHead>SKU</TableHead>
                        <TableHead>Quantity</TableHead>
                        <TableHead>Reorder</TableHead>
                        <TableHead>Status</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {items.map((inv) => {
                        const isLow = inv.product.reorderLevel != null && inv.quantity <= inv.product.reorderLevel;
                        return (
                          <TableRow key={inv.id}>
                            <TableCell className="font-medium text-sm">{inv.product.productName}</TableCell>
                            <TableCell className="font-mono text-xs text-gray-500">{inv.product.sku ?? "—"}</TableCell>
                            <TableCell>
                              <span className={isLow ? "text-red-600 font-bold" : "font-semibold"}>
                                {inv.quantity}
                              </span>
                              {inv.product.unit && <span className="text-xs text-gray-400 ml-1">{inv.product.unit.symbol}</span>}
                            </TableCell>
                            <TableCell className="text-sm">{inv.product.reorderLevel ?? "—"}</TableCell>
                            <TableCell>
                              {inv.quantity === 0
                                ? <Badge variant="destructive">Out of stock</Badge>
                                : isLow
                                ? <Badge variant="warning">Low stock</Badge>
                                : <Badge variant="success">In stock</Badge>}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </div>
              )}
            </TabsContent>
          ))}
        </Tabs>
      )}

      {/* Stock In Modal */}
      <Dialog open={modal === "in"} onOpenChange={(o) => !o && setModal(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>Stock In</DialogTitle></DialogHeader>
          <form onSubmit={handleSubmit(onStockIn)} className="space-y-4">
            <div className="space-y-1.5">
              <Label>Product *</Label>
              <Select onValueChange={(v) => setValue("productId", Number(v))}>
                <SelectTrigger><SelectValue placeholder="Select product" /></SelectTrigger>
                <SelectContent>
                  {prods.map((p) => <SelectItem key={p.id} value={String(p.id)}>{p.productName}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Shop *</Label>
              <Select onValueChange={(v) => setValue("shopId", Number(v))}>
                <SelectTrigger><SelectValue placeholder="Select shop" /></SelectTrigger>
                <SelectContent>
                  {shops.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.shopName}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Quantity *</Label>
                <Input type="number" min="0.01" step="any" {...register("quantity")} />
                {errors.quantity && <p className="text-xs text-red-600">{String(errors.quantity.message)}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>Cost price (optional)</Label>
                <Input type="number" step="0.01" {...register("costPrice")} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Supplier (optional)</Label>
              <Select onValueChange={(v) => setValue("supplierId", Number(v))}>
                <SelectTrigger><SelectValue placeholder="Select supplier" /></SelectTrigger>
                <SelectContent>
                  {suppliers.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.companyName}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Notes</Label>
              <Input {...register("notes")} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setModal(null)}>Cancel</Button>
              <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : "Stock In"}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
