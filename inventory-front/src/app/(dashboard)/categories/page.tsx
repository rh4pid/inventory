"use client";
import { errorMessage } from "@/lib/utils";
import { useEffect, useState, useCallback } from "react";
import { Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useForm, type Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useBusiness } from "@/context/BusinessContext";
import { categories as catsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import type { Category } from "@/Types";

const schema = z.object({
  categoryName: z.string().min(1, "Required"),
  parentId: z.coerce.number().optional(),
});
type FormData = z.infer<typeof schema>;

export default function CategoriesPage() {
  const { activeBusiness } = useBusiness();
  const [cats, setCats] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);

  const { register, handleSubmit, setValue, reset, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema) as Resolver<FormData>,
  });

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    setLoading(true);
    try {
      const list = await catsApi.list(activeBusiness.businessId);
      setCats(list);
    } catch (err) {
      toast.error(errorMessage(err));
    } finally { setLoading(false); }
  }, [activeBusiness]);

  useEffect(() => { load(); }, [load]);

  const onSubmit = async (data: FormData) => {
    if (!activeBusiness) return;
    try {
      await catsApi.create(activeBusiness.businessId, {
        categoryName: data.categoryName,
        parentId: data.parentId || undefined,
      });
      toast.success("Category created");
      reset();
      setOpen(false);
      load();
    } catch (err) { toast.error(errorMessage(err)); }
  };

  const handleDelete = async () => {
    if (!activeBusiness || deleteId == null) return;
    setDeleting(true);
    try {
      await catsApi.delete(activeBusiness.businessId, deleteId);
      toast.success("Deleted");
      setDeleteId(null);
      load();
    } catch (err) { toast.error(errorMessage(err)); }
    setDeleting(false);
  };

  const roots = cats.filter((c) => !c.parent);

  if (loading && cats.length === 0) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Categories"
        description={`${cats.length} categories`}
        action={<Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" /> Add Category</Button>}
      />

      {cats.length === 0 ? (
        <EmptyState
          title="No categories"
          description="Organize your products with categories."
          action={<Button onClick={() => setOpen(true)}>Add Category</Button>}
        />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {roots.map((cat) => (
            <div key={cat.id} className="rounded-3xl border border-line/80 bg-surface shadow-card p-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-sm">{cat.categoryName}</p>
                  {cat.parent && (
                    <p className="text-xs text-gray-400 mt-0.5">Sub of {cat.parent.categoryName}</p>
                  )}
                </div>
                <div className="flex gap-1">
                  <Badge variant="secondary" className="text-xs">
                    {cats.filter((c) => c.parent?.id === cat.id).length} sub
                  </Badge>
                  <Button
                    variant="ghost" size="icon" className="h-7 w-7 text-red-500 hover:text-red-600"
                    onClick={() => setDeleteId(cat.id)}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>New Category</DialogTitle></DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-1.5">
              <Label>Name *</Label>
              <Input placeholder="Electronics" {...register("categoryName")} />
              {errors.categoryName && <p className="text-xs text-red-600">{errors.categoryName.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label>Parent category (optional)</Label>
              <Select onValueChange={(v) => setValue("parentId", Number(v))}>
                <SelectTrigger><SelectValue placeholder="None (top-level)" /></SelectTrigger>
                <SelectContent>
                  {cats.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.categoryName}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Saving…" : "Create"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteId != null}
        onOpenChange={(o) => !o && setDeleteId(null)}
        title="Delete category?"
        description="Products in this category will be unassigned."
        onConfirm={handleDelete}
        loading={deleting}
      />
    </div>
  );
}
