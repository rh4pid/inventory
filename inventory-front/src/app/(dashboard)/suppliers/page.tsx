"use client";
import { DataView, ViewToggle, useViewMode } from "@/components/shared/DataView";
import { ImportButton } from "@/components/imports/ImportButton";
import { errorMessage } from "@/lib/utils";
import { useEffect, useState, useCallback } from "react";
import { Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useBusiness } from "@/context/BusinessContext";
import { suppliers as suppliersApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Supplier } from "@/Types";

const schema = z.object({
  companyName: z.string().min(1, "Required"),
  relationshipOfficer: z.string().optional(),
  companyEmail: z.string().email("Invalid email").optional().or(z.literal("")),
  telephonePrimary: z.string().optional(),
  telephoneSecondary: z.string().optional(),
});
type FormData = z.infer<typeof schema>;

export default function SuppliersPage() {
  const [view, setView] = useViewMode("suppliers");
  const { activeBusiness } = useBusiness();
  const [items, setItems] = useState<Supplier[]>([]);
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
      const res = await suppliersApi.list(activeBusiness.businessId, { page, size: SIZE });
      setItems(res.content);
      setTotal(res.totalElements);
    } catch (err) { toast.error(errorMessage(err)); }
    setLoading(false);
  }, [activeBusiness, page]);

  useEffect(() => { load(); }, [load]);

  const onSubmit = async (data: FormData) => {
    if (!activeBusiness) return;
    try {
      await suppliersApi.create(activeBusiness.businessId, {
        ...data, companyEmail: data.companyEmail || undefined,
      });
      toast.success("Supplier added");
      reset(); setOpen(false); load();
    } catch (err) { toast.error(errorMessage(err)); }
  };

  const handleDelete = async () => {
    if (!activeBusiness || deleteId == null) return;
    setDeleting(true);
    try {
      await suppliersApi.delete(activeBusiness.businessId, deleteId);
      toast.success("Supplier deleted");
      setDeleteId(null); load();
    } catch (err) { toast.error(errorMessage(err)); }
    setDeleting(false);
  };

  if (loading && items.length === 0) return <PageLoader />;

  return (
    <div>
      <PageHeader view={<ViewToggle mode={view} onChange={setView} />}
        title="Suppliers"
        description={`${total} suppliers`}
        action={
          <div className="flex flex-wrap gap-2">
            <ImportButton kind="suppliers" onDone={load} />
            <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" /> Add Supplier</Button>
          </div>
        }
      />

      {items.length === 0 && !loading ? (
        <EmptyState title="No suppliers" description="Add suppliers to track purchase orders."
          action={<Button onClick={() => setOpen(true)}>Add Supplier</Button>} />
      ) : (
        <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-hidden">
          <DataView mode={view}><Table>
            <TableHeader>
              <TableRow>
                <TableHead>Company</TableHead>
                <TableHead>Contact Person</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Phone</TableHead>
                <TableHead className="w-16"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((s) => (
                <TableRow key={s.id}>
                  <TableCell className="font-medium text-sm">{s.companyName}</TableCell>
                  <TableCell className="text-sm text-gray-500">{s.relationshipOfficer ?? "—"}</TableCell>
                  <TableCell className="text-sm text-gray-500">{s.companyEmail ?? "—"}</TableCell>
                  <TableCell className="text-sm text-gray-500">{s.telephonePrimary ?? "—"}</TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" className="h-8 w-8 text-red-500"
                      onClick={() => setDeleteId(s.id)}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table></DataView>

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
          <DialogHeader><DialogTitle>New Supplier</DialogTitle></DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-1.5">
              <Label>Company name *</Label>
              <Input {...register("companyName")} />
              {errors.companyName && <p className="text-xs text-red-600">{errors.companyName.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label>Contact person</Label>
              <Input {...register("relationshipOfficer")} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Email</Label>
                <Input type="email" {...register("companyEmail")} />
                {errors.companyEmail && <p className="text-xs text-red-600">{errors.companyEmail.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>Phone</Label>
                <Input type="tel" {...register("telephonePrimary")} />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : "Add Supplier"}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteId != null}
        onOpenChange={(o) => !o && setDeleteId(null)}
        title="Delete supplier?"
        description="Past purchase records will not be affected."
        onConfirm={handleDelete}
        loading={deleting}
      />
    </div>
  );
}
