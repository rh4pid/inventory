"use client";
import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { sales as salesApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PaymentStatusBadge, InvoiceStatusBadge } from "@/components/shared/StatusBadge";
import { formatCurrency, formatDate } from "@/lib/utils";
import { InvoiceDialog } from "@/components/shared/InvoiceDialog";
import type { Invoice } from "@/Types";

export default function SalesPage() {
  const { activeBusiness } = useBusiness();
  const [items, setItems] = useState<Invoice[]>([]);
  const [opened, setOpened] = useState<Invoice | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  const SIZE = 20;

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    setLoading(true);
    try {
      const res = await salesApi.list(activeBusiness.businessId, { page, size: SIZE });
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
        title="Sales"
        description={`${total} transactions`}
        action={
          <Link href="/sales/new">
            <Button><Plus className="h-4 w-4" /> New Sale</Button>
          </Link>
        }
      />

      {items.length === 0 && !loading ? (
        <EmptyState
          title="No sales yet"
          description="Record your first sale to get started."
          action={<Link href="/sales/new"><Button>New Sale</Button></Link>}
        />
      ) : (
        <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Invoice #</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Shop</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Payment</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Date</TableHead>
                <TableHead className="w-20" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((s) => (
                <TableRow key={s.id} className="cursor-pointer hover:bg-gray-50" onClick={() => setOpened(s)}>
                  <TableCell className="font-mono text-xs">{s.invoiceNumber}</TableCell>
                  <TableCell className="text-sm">
                    {s.customer
                      ? `${s.customer.firstName} ${s.customer.lastName ?? ""}`
                      : <span className="text-gray-400">Walk-in</span>}
                  </TableCell>
                  <TableCell className="text-sm text-gray-500">{s.shop?.shopName ?? "—"}</TableCell>
                  <TableCell className="font-semibold">{formatCurrency(s.totalAmount)}</TableCell>
                  <TableCell><PaymentStatusBadge status={s.paymentStatus} /></TableCell>
                  <TableCell><InvoiceStatusBadge status={s.invoiceStatus} /></TableCell>
                  <TableCell className="text-xs text-gray-500">
                    {s.createdAt ? formatDate(s.createdAt) : "—"}
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" size="sm" onClick={(e) => { e.stopPropagation(); setOpened(s); }}>Open</Button>
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
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  Previous
                </Button>
                <Button variant="outline" size="sm" disabled={(page + 1) * SIZE >= total} onClick={() => setPage((p) => p + 1)}>
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
      {opened && (
        <InvoiceDialog invoice={opened} onClose={() => setOpened(null)} onChanged={load} />
      )}
    </div>
  );
}
