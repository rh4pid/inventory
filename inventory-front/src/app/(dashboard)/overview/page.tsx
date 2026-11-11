"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Package, ShoppingCart, AlertTriangle, TrendingUp, Plus } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { inventory, products, sales } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { StatCard } from "@/components/shared/StatCard";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PaymentStatusBadge } from "@/components/shared/StatusBadge";
import { formatCurrency, formatDate } from "@/lib/utils";
import type { Invoice, Inventory } from "@/Types";

export default function OverviewPage() {
  const { activeBusiness } = useBusiness();
  const [loading, setLoading] = useState(true);
  const [recentSales, setRecentSales] = useState<Invoice[]>([]);
  const [lowStockItems, setLowStockItems] = useState<Inventory[]>([]);
  const [totalProducts, setTotalProducts] = useState(0);

  useEffect(() => {
    if (!activeBusiness) return;
    const id = activeBusiness.businessId;
    setLoading(true);

    Promise.all([
      sales.list(id, { size: 5 }),
      inventory.lowStock(id),
      products.list(id, { size: 1 }),
    ]).then(([salesPage, lowStock, productPage]) => {
      setRecentSales(salesPage.content);
      setLowStockItems(lowStock);
      setTotalProducts(productPage.totalElements);
    }).catch(() => {}).finally(() => setLoading(false));
  }, [activeBusiness]);

  if (!activeBusiness) {
    return (
      <EmptyState
        title="No business selected"
        description="You are not a member of any business yet."
      />
    );
  }

  if (loading) return <PageLoader />;

  const todayRevenue = recentSales
    .filter((s) => {
      const d = new Date(s.createdAt ?? "");
      const today = new Date();
      return d.toDateString() === today.toDateString();
    })
    .reduce((acc, s) => acc + parseFloat(s.totalAmount), 0);

  return (
    <div>
      <PageHeader
        title={`Welcome back`}
        description={activeBusiness.businessName}
        action={
          <Link href="/sales/new">
            <Button>
              <Plus className="h-4 w-4" /> New Sale
            </Button>
          </Link>
        }
      />

      {/* Stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-6">
        <StatCard
          title="Total Products"
          value={totalProducts}
          icon={<Package className="h-6 w-6" />}
          description="Active catalog items"
        />
        <StatCard
          title="Today's Revenue"
          value={formatCurrency(todayRevenue)}
          icon={<TrendingUp className="h-6 w-6" />}
        />
        <StatCard
          title="Recent Sales"
          value={recentSales.length}
          icon={<ShoppingCart className="h-6 w-6" />}
          description="Last 5 transactions"
        />
        <StatCard
          title="Low Stock Alerts"
          value={lowStockItems.length}
          icon={<AlertTriangle className="h-6 w-6" />}
          className={lowStockItems.length > 0 ? "border-yellow-300 bg-yellow-50" : ""}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Recent Sales */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-3">
            <CardTitle className="text-base">Recent Sales</CardTitle>
            <Link href="/sales" className="text-xs text-blue-600 hover:underline">View all</Link>
          </CardHeader>
          <CardContent className="p-0">
            {recentSales.length === 0 ? (
              <EmptyState title="No sales yet" className="py-8" />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Invoice</TableHead>
                    <TableHead>Customer</TableHead>
                    <TableHead>Total</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {recentSales.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell className="font-mono text-xs">{s.invoiceNumber}</TableCell>
                      <TableCell className="text-sm">
                        {s.customer ? `${s.customer.firstName} ${s.customer.lastName ?? ""}` : "Walk-in"}
                      </TableCell>
                      <TableCell className="font-medium">{formatCurrency(s.totalAmount)}</TableCell>
                      <TableCell><PaymentStatusBadge status={s.paymentStatus} /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        {/* Low Stock */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-3">
            <CardTitle className="text-base">Low Stock Alerts</CardTitle>
            <Link href="/inventory" className="text-xs text-blue-600 hover:underline">Manage</Link>
          </CardHeader>
          <CardContent className="p-0">
            {lowStockItems.length === 0 ? (
              <EmptyState title="All stock levels OK" icon={<Package className="h-10 w-10 text-green-300" />} className="py-8" />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead>Shop</TableHead>
                    <TableHead>Qty</TableHead>
                    <TableHead>Reorder</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {lowStockItems.slice(0, 5).map((inv) => (
                    <TableRow key={inv.id}>
                      <TableCell className="text-sm font-medium">{inv.product.productName}</TableCell>
                      <TableCell className="text-xs text-gray-500">{inv.shop.shopName}</TableCell>
                      <TableCell>
                        <span className="text-red-600 font-semibold">{inv.quantity}</span>
                      </TableCell>
                      <TableCell className="text-xs text-gray-500">
                        {inv.product.reorderLevel ?? "—"}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
