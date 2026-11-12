"use client";
import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import {
  AlertTriangle, ArrowDownToLine, ArrowUpFromLine, ArrowRightLeft, CalendarClock, ClipboardCheck, ClipboardList, SlidersHorizontal,
} from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { inventory as invApi, products as productsApi, suppliers as suppliersApi, shops as shopsApi, stockRequests as requestsApi } from "@/lib/api";
import { MOVEMENT_LABELS, fmtQty, locationTypeLabel } from "@/constants/Inventory";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { StockActionDialog, type StockAction, type StockActionPreset } from "@/components/inventory/StockActionDialog";
import { BatchStatusBadge, formatDate } from "@/components/inventory/BatchStatusBadge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { Inventory, Product, Supplier, Shop, StockBatch, StockMovement } from "@/Types";

type View = "stock" | "batches" | "history";

export default function InventoryPage() {
  const { activeBusiness } = useBusiness();
  const { settings } = useBusinessSettings();
  const [items, setItems] = useState<Inventory[]>([]);
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [batches, setBatches] = useState<StockBatch[]>([]);
  const [expiring, setExpiring] = useState<StockBatch[]>([]);
  const [lowStock, setLowStock] = useState<Inventory[]>([]);
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState<StockAction | null>(null);
  const [preset, setPreset] = useState<StockActionPreset | undefined>(undefined);
  const [prods, setProds] = useState<Product[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [shops, setShops] = useState<Shop[]>([]);       // locations this user works at
  const [allShops, setAllShops] = useState<Shop[]>([]); // every location (transfer destinations)
  const [activeShop, setActiveShop] = useState<string>("");
  const [view, setView] = useState<View>("stock");
  const [pendingApprovals, setPendingApprovals] = useState(0);

  const businessId = activeBusiness?.businessId;
  const trackBatches = Boolean(settings?.trackBatches);
  const trackExpiry = Boolean(settings?.trackExpiry);

  const loadShop = useCallback(async (shopId: string) => {
    if (!businessId || !shopId) return;
    try {
      const [inv, mv, bt] = await Promise.all([
        invApi.listByShop(businessId, Number(shopId), { size: 200 }),
        invApi.movements(businessId, { shopId: Number(shopId), size: 50 }),
        trackBatches ? invApi.batches(businessId, { shopId: Number(shopId) }) : Promise.resolve([]),
      ]);
      setItems(inv.content);
      setMovements(mv.content);
      setBatches(bt);
    } catch (err: any) { toast.error(err.message); }
  }, [businessId, trackBatches]);

  const load = useCallback(async () => {
    if (!businessId) return;
    setLoading(true);
    try {
      const [low, shopRes, allShopRes, prodRes, supRes, exp, pending] = await Promise.all([
        invApi.lowStock(businessId),
        shopsApi.list(businessId, { mine: true }),
        shopsApi.list(businessId),
        productsApi.list(businessId, { size: 200 }),
        suppliersApi.list(businessId, { size: 100 }),
        trackExpiry ? invApi.expiring(businessId) : Promise.resolve([]),
        requestsApi.pendingCount(businessId).catch(() => ({ count: 0 })),
      ]);
      setLowStock(low);
      setShops(shopRes.content);
      setAllShops(allShopRes.content);
      setProds(prodRes.content);
      setSuppliers(supRes.content);
      setExpiring(exp);
      setPendingApprovals(pending.count);
      const current = shopRes.content.find((s) => String(s.id) === activeShop) ?? shopRes.content[0];
      if (current) {
        setActiveShop(String(current.id));
        await loadShop(String(current.id));
      }
    } catch (err: any) { toast.error(err.message); }
    setLoading(false);
    // activeShop intentionally omitted: reloading keeps the current tab without re-triggering.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [businessId, loadShop, trackExpiry]);

  useEffect(() => { load(); }, [load]);

  const handleTabChange = (shopId: string) => {
    setActiveShop(shopId);
    loadShop(shopId);
  };

  const open = (a: StockAction, p?: StockActionPreset) => {
    setPreset(p ?? (activeShop ? { shopId: Number(activeShop) } : undefined));
    setAction(a);
  };

  if (loading && shops.length === 0) return <PageLoader />;

  const ACTIONS: { key: StockAction; label: string; icon: typeof ArrowDownToLine }[] = [
    { key: "in", label: "Stock In", icon: ArrowDownToLine },
    { key: "out", label: "Stock Out", icon: ArrowUpFromLine },
    { key: "transfer", label: "Transfer", icon: ArrowRightLeft },
    { key: "adjust", label: "Adjust", icon: SlidersHorizontal },
  ];

  const expired = expiring.filter((b) => b.status === "EXPIRED");
  const soon = expiring.filter((b) => b.status === "EXPIRING");
  const views: { key: View; label: string }[] = [
    { key: "stock", label: "Stock on hand" },
    ...(trackBatches ? [{ key: "batches" as View, label: "Batches" }] : []),
    { key: "history", label: "Movement history" },
  ];
  const currentView: View = view === "batches" && !trackBatches ? "stock" : view;

  return (
    <div>
      <PageHeader
        title="Inventory"
        description="Track stock levels and every movement across your locations"
        action={
          <div className="flex flex-wrap gap-2">
            <Link href="/inventory/counts">
              <Button variant="outline" size="sm"><ClipboardList className="h-4 w-4" /> Stock counts</Button>
            </Link>
            <Link href="/inventory/approvals">
              <Button variant="outline" size="sm">
                <ClipboardCheck className="h-4 w-4" /> Approvals
                {pendingApprovals > 0 && (
                  <span className="ml-1 rounded-full bg-red-600 px-1.5 text-[11px] font-semibold text-white">{pendingApprovals}</span>
                )}
              </Button>
            </Link>
            {ACTIONS.map((a) => (
              <Button key={a.key} variant="outline" size="sm" onClick={() => open(a.key)} disabled={shops.length === 0}>
                <a.icon className="h-4 w-4" /> {a.label}
              </Button>
            ))}
          </div>
        }
      />

      {trackExpiry && (expired.length > 0 || soon.length > 0) && (
        <div className={`mb-4 flex flex-wrap items-center gap-3 rounded-lg border px-4 py-3 ${
          expired.length > 0 ? "border-red-200 bg-red-50" : "border-orange-200 bg-orange-50"}`}>
          <CalendarClock className={`h-5 w-5 shrink-0 ${expired.length > 0 ? "text-red-600" : "text-orange-600"}`} />
          <p className={`text-sm flex-1 ${expired.length > 0 ? "text-red-800" : "text-orange-800"}`}>
            {expired.length > 0 && <><strong>{expired.length}</strong> batch{expired.length > 1 ? "es" : ""} expired</>}
            {expired.length > 0 && soon.length > 0 && " · "}
            {soon.length > 0 && <><strong>{soon.length}</strong> expiring within {settings?.expiryAlertDays ?? 30} days</>}
            <span className="block text-xs opacity-80">
              {expiring.slice(0, 3).map((b) => `${b.productName} (${b.batchNumber}, ${b.shopName})`).join(" · ")}
              {expiring.length > 3 ? ` · +${expiring.length - 3} more` : ""}
            </span>
          </p>
          <Button variant="outline" size="sm" onClick={() => setView("batches")}>Review batches</Button>
        </div>
      )}

      {settings?.lowStockAlerts !== false && lowStock.length > 0 && (
        <div className="mb-4 flex items-center gap-3 rounded-lg border border-yellow-200 bg-yellow-50 px-4 py-3">
          <AlertTriangle className="h-5 w-5 text-yellow-600 shrink-0" />
          <p className="text-sm text-yellow-800">
            <strong>{lowStock.length}</strong> stock line{lowStock.length > 1 ? "s" : ""} at or below reorder level.
          </p>
        </div>
      )}

      {shops.length === 0 ? (
        <EmptyState
          title="No locations yet"
          description="Add a shop, warehouse or storeroom under Settings → Locations to start tracking stock."
        />
      ) : (
        <>
          <Tabs value={activeShop} onValueChange={handleTabChange}>
            <TabsList className="mb-4 flex-wrap h-auto">
              {shops.map((s) => (
                <TabsTrigger key={s.id} value={String(s.id)}>
                  {s.shopName}
                  <span className="ml-1.5 text-[10px] uppercase text-gray-400">{locationTypeLabel(s.locationType).split(" ")[0]}</span>
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>

          <div className="mb-3 flex gap-1 text-sm" role="tablist" aria-label="Inventory view">
            {views.map((v) => (
              <button
                key={v.key} role="tab" aria-selected={currentView === v.key} onClick={() => setView(v.key)}
                className={`rounded-md px-3 py-1.5 font-medium ${currentView === v.key ? "bg-gray-900 text-white" : "text-gray-600 hover:bg-gray-100"}`}
              >
                {v.label}
              </button>
            ))}
          </div>

          {currentView === "stock" && (items.length === 0 ? (
            <EmptyState title="No stock here yet" description="Use Stock In or Transfer to add products to this location." />
          ) : (
            <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead>SKU</TableHead>
                    <TableHead>Quantity</TableHead>
                    <TableHead>Reorder at</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((inv) => {
                    const isLow = inv.product.reorderLevel != null && inv.quantity <= inv.product.reorderLevel;
                    return (
                      <TableRow key={inv.id}>
                        <TableCell className="font-medium text-sm">
                          <Link href={`/products/${inv.product.id}/edit`} className="hover:underline">{inv.product.productName}</Link>
                        </TableCell>
                        <TableCell className="font-mono text-xs text-gray-500">{inv.product.sku ?? "—"}</TableCell>
                        <TableCell>
                          <span className={inv.quantity <= 0 || isLow ? "text-red-600 font-bold" : "font-semibold"}>
                            {fmtQty(inv.quantity)}
                          </span>
                          {inv.product.unit && (
                            <span className="text-xs text-gray-400 ml-1">{inv.product.unit.symbol ?? inv.product.unit.name}</span>
                          )}
                        </TableCell>
                        <TableCell className="text-sm">{inv.product.reorderLevel ?? "—"}</TableCell>
                        <TableCell>
                          {inv.quantity < 0
                            ? <Badge variant="destructive">Negative</Badge>
                            : inv.quantity === 0
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
          ))}

          {currentView === "batches" && (batches.length === 0 ? (
            <EmptyState title="No batches here" description="Batches are created when stock is received at this location." />
          ) : (
            <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead>Batch / lot</TableHead>
                    {trackExpiry && <TableHead>Expiry</TableHead>}
                    <TableHead className="text-right">Remaining</TableHead>
                    <TableHead>Received</TableHead>
                    <TableHead>Source</TableHead>
                    <TableHead className="w-40" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {batches.map((b) => (
                    <TableRow key={b.id} className={b.status === "EXPIRED" ? "bg-red-50/50" : undefined}>
                      <TableCell className="text-sm font-medium">{b.productName}</TableCell>
                      <TableCell className="font-mono text-xs">{b.batchNumber}</TableCell>
                      {trackExpiry && (
                        <TableCell className="text-sm whitespace-nowrap">
                          <span className="mr-2">{formatDate(b.expiryDate)}</span>
                          <BatchStatusBadge batch={b} />
                        </TableCell>
                      )}
                      <TableCell className="text-right text-sm font-semibold">
                        {fmtQty(b.quantityRemaining)}
                        <span className="ml-1 text-xs font-normal text-gray-400">{b.baseUnit ?? ""}</span>
                      </TableCell>
                      <TableCell className="text-xs text-gray-500">{formatDate(b.receivedAt)}</TableCell>
                      <TableCell className="text-xs text-gray-500">{b.source ?? "—"}</TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="sm"
                            onClick={() => open("out", { productId: b.productId, shopId: b.shopId, batchId: b.id })}>
                            {b.status === "EXPIRED" ? "Write off" : "Remove"}
                          </Button>
                          <Button variant="ghost" size="sm"
                            onClick={() => open("transfer", { productId: b.productId, shopId: b.shopId, batchId: b.id })}>
                            Move
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ))}

          {currentView === "history" && (movements.length === 0 ? (
            <EmptyState title="No movements yet" description="Every stock in, sale, transfer and adjustment will appear here." />
          ) : (
            <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>When</TableHead>
                    <TableHead>Product</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead className="text-right">Change</TableHead>
                    <TableHead className="text-right">Balance</TableHead>
                    <TableHead>Details</TableHead>
                    <TableHead>By</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {movements.map((m) => (
                    <TableRow key={m.id}>
                      <TableCell className="text-xs text-gray-500 whitespace-nowrap">
                        {m.createdAt ? new Date(m.createdAt).toLocaleString() : "—"}
                      </TableCell>
                      <TableCell className="text-sm font-medium">{m.product.productName}</TableCell>
                      <TableCell><Badge variant="secondary">{MOVEMENT_LABELS[m.movementType] ?? m.movementType}</Badge></TableCell>
                      <TableCell className={`text-right font-semibold text-sm ${m.quantityChange < 0 ? "text-red-600" : "text-green-700"}`}>
                        {m.quantityChange > 0 ? "+" : ""}{fmtQty(m.quantityChange)}
                        {m.enteredUnit && m.enteredQuantity != null && (
                          <span className="block text-[11px] font-normal text-gray-400">
                            {fmtQty(m.enteredQuantity)} × {m.enteredUnit}
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right text-sm">{fmtQty(m.quantityAfter)}</TableCell>
                      <TableCell className="text-xs text-gray-600 max-w-xs">
                        {m.notes ?? (m.batchNumber ? "" : "—")}
                        {m.batchNumber && (
                          <span className="block font-mono text-[11px] text-gray-400">Batch {m.batchNumber}</span>
                        )}
                      </TableCell>
                      <TableCell className="text-xs text-gray-500">{m.performedBy ?? "—"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ))}
        </>
      )}

      {businessId && (
        <StockActionDialog
          key={action ? `${action}-${preset?.batchId ?? ""}-${preset?.productId ?? ""}` : "closed"}
          action={action}
          onClose={() => setAction(null)}
          onDone={load}
          businessId={businessId}
          products={prods}
          shops={shops}
          destinations={allShops}
          suppliers={suppliers}
          preset={preset}
          multipleUnits={Boolean(settings?.multipleUnits)}
          trackBatches={trackBatches}
          trackExpiry={trackExpiry}
          needsApproval={Boolean(settings?.requireAdjustmentApproval) && !["OWNER", "ADMIN", "MANAGER"].includes(activeBusiness?.role ?? "")}
        />
      )}
    </div>
  );
}
