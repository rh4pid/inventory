"use client";
import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { purchases as purchasesApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PaymentStatusBadge, InvoiceStatusBadge } from "@/components/shared/StatusBadge";
import { formatCurrency, formatDate } from "@/lib/utils";
import type { Invoice } from "@/Types";

export default function PurchasesPage() {
  const { activeBusiness } = useBusiness();
  const [items, setItems] = useState<Invoice[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  const SIZE = 20;

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    setLoading(true);
    try {
      const res = await purchasesApi.list(activeBusiness.businessId, { page, size: SIZE });
      setItems(res.content);
      setTotal(res.totalElements);
    } catch (err: any) { toast.error(err.message); }
    setLoading(false);
  }, [activeBusiness, page]);

  useEffect(() => { load(); }, [load]);

  if (loading && items.length === 0) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Purchases"
        description={`${total} purchase orders`}
        action={
          <Link href="/purchases/new">
            <Button><Plus className="h-4 w-4" /> New Purchase</Button>
          </Link>
        }
      />

      {items.length === 0 && !loading ? (
        <EmptyState
          title="No purchases yet"
          description="Record supplier purchases to track inventory and costs."
          action={<Link href="/purchases/new"><Button>New Purchase</Button></Link>}
        />
      ) : (
        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Invoice #</TableHead>
                <TableHead>Supplier</TableHead>
                <TableHead>Shop</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Payment</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Date</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((p) => (
                <TableRow key={p.id}>
                  <TableCell className="font-mono text-xs">{p.invoiceNumber}</TableCell>
                  <TableCell className="text-sm">
                    {p.supplier?.companyName ?? <span className="text-gray-400">—</span>}
                  </TableCell>
                  <TableCell className="text-sm text-gray-500">{p.shop?.shopName ?? "—"}</TableCell>
                  <TableCell className="font-semibold">{formatCurrency(p.totalAmount)}</TableCell>
                  <TableCell><PaymentStatusBadge status={p.paymentStatus} /></TableCell>
                  <TableCell><InvoiceStatusBadge status={p.invoiceStatus} /></TableCell>
                  <TableCell className="text-xs text-gray-500">
                    {p.createdAt ? formatDate(p.createdAt) : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {total > SIZE && (
            <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
              <p className="text-xs text-gray-500">
                {page * SIZE + 1}–{Math.min((page + 1) * SIZE, total)} of {total}
              </p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
                <Button variant="outline" size="sm" disabled={(page + 1) * SIZE >= total} onClick={() => setPage((p) => p + 1)}>Next</Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
