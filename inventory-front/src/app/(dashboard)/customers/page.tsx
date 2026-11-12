"use client";
import { useEffect, useState, useCallback } from "react";
import { Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useCan } from "@/lib/access";
import { useBusiness } from "@/context/BusinessContext";
import { customers as customersApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatCurrency } from "@/lib/utils";
import type { Customer } from "@/Types";

const schema = z.object({
  firstName: z.string().min(1, "Required"),
  lastName: z.string().optional(),
  email: z.string().email("Invalid email").optional().or(z.literal("")),
  phone: z.string().optional(),
  address: z.string().optional(),
});
type FormData = z.infer<typeof schema>;

export default function CustomersPage() {
  const canDelete = useCan()("DELETE_CUSTOMERS");
  const { activeBusiness } = useBusiness();
  const [items, setItems] = useState<Customer[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);

  const SIZE = 20;
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    setLoading(true);
    try {
      const res = await customersApi.list(activeBusiness.businessId, { page, size: SIZE });
      setItems(res.content);
      setTotal(res.totalElements);
    } catch (err: any) { toast.error(err.message); }
    setLoading(false);
  }, [activeBusiness, page]);

  useEffect(() => { load(); }, [load]);

  const onSubmit = async (data: FormData) => {
    if (!activeBusiness) return;
    try {
      await customersApi.create(activeBusiness.businessId, {
        ...data, email: data.email || undefined,
      });
      toast.success("Customer added");
      reset(); setOpen(false); load();
    } catch (err: any) { toast.error(err.message); }
  };

  const handleDelete = async () => {
    if (!activeBusiness || deleteId == null) return;
    setDeleting(true);
    try {
      await customersApi.delete(activeBusiness.businessId, deleteId);
      toast.success("Customer deleted");
      setDeleteId(null); load();
    } catch (err: any) { toast.error(err.message); }
    setDeleting(false);
  };

  if (loading && items.length === 0) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Customers"
        description={`${total} customers`}
        action={<Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" /> Add Customer</Button>}
      />

      {items.length === 0 && !loading ? (
        <EmptyState title="No customers" description="Add customers to track sales history and loyalty."
          action={<Button onClick={() => setOpen(true)}>Add Customer</Button>} />
      ) : (
        <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Phone</TableHead>
                <TableHead>Total Purchases</TableHead>
                <TableHead className="w-16"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((c) => (
                <TableRow key={c.id}>
                  <TableCell className="font-medium text-sm">{c.firstName} {c.lastName}</TableCell>
                  <TableCell className="text-sm text-gray-500">{c.email ?? "—"}</TableCell>
                  <TableCell className="text-sm text-gray-500">{c.phone ?? "—"}</TableCell>
                  <TableCell className="font-medium">{formatCurrency(c.totalPurchases)}</TableCell>
                  <TableCell>
                    {canDelete && <Button variant="ghost" size="icon" className="h-8 w-8 text-red-500"
                      onClick={() => setDeleteId(c.id)} aria-label={`Delete ${c.firstName}`}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {total > SIZE && (
            <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
              <p className="text-xs text-gray-500">{page * SIZE + 1}–{Math.min((page + 1) * SIZE, total)} of {total}</p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
                <Button variant="outline" size="sm" disabled={(page + 1) * SIZE >= total} onClick={() => setPage((p) => p + 1)}>Next</Button>
              </div>
            </div>
          )}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>New Customer</DialogTitle></DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>First name *</Label>
                <Input {...register("firstName")} />
                {errors.firstName && <p className="text-xs text-red-600">{errors.firstName.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>Last name</Label>
                <Input {...register("lastName")} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Email</Label>
              <Input type="email" {...register("email")} />
              {errors.email && <p className="text-xs text-red-600">{errors.email.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label>Phone</Label>
              <Input type="tel" {...register("phone")} />
            </div>
            <div className="space-y-1.5">
              <Label>Address</Label>
              <Input {...register("address")} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : "Add Customer"}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteId != null}
        onOpenChange={(o) => !o && setDeleteId(null)}
        title="Delete customer?"
        description="Past sales records will not be affected."
        onConfirm={handleDelete}
        loading={deleting}
      />
    </div>
  );
}
