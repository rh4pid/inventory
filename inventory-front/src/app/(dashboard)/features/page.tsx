"use client";
import { useCallback, useEffect, useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { features as featuresApi } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { DataView, ViewToggle, useViewMode } from "@/components/shared/DataView";
import { ImportButton } from "@/components/imports/ImportButton";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Feature } from "@/Types";

const MAX_VALUES_SHOWN = 8;

/** Features such as Color, Size or Brand: defined once, then each product gets its own value. */
export default function FeaturesPage() {
  const [view, setView] = useViewMode("features");
  const { activeBusiness } = useBusiness();
  const bizId = activeBusiness?.businessId;
  const [list, setList] = useState<Feature[] | null>(null);
  const [editing, setEditing] = useState<Feature | "new" | null>(null);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<Feature | null>(null);
  const [deletingBusy, setDeletingBusy] = useState(false);

  const load = useCallback(() => {
    if (!bizId) return Promise.resolve();
    return featuresApi.list(bizId)
      .then(setList)
      .catch((err) => { toast.error(errorMessage(err, "Could not load features")); setList([]); });
  }, [bizId]);

  useEffect(() => {
    if (!bizId) return;
    let live = true;
    featuresApi.list(bizId)
      .then((l) => { if (live) setList(l); })
      .catch((err) => { if (live) { toast.error(errorMessage(err, "Could not load features")); setList([]); } });
    return () => { live = false; };
  }, [bizId]);

  const open = (f: Feature | "new") => { setEditing(f); setName(f === "new" ? "" : f.name); };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bizId || !editing || !name.trim()) return;
    setSaving(true);
    try {
      if (editing === "new") await featuresApi.create(bizId, name.trim());
      else await featuresApi.rename(bizId, editing.id, name.trim());
      toast.success(editing === "new" ? `Feature "${name.trim()}" added` : "Feature renamed");
      setEditing(null);
      void load();
    } catch (err) {
      toast.error(errorMessage(err, "Could not save the feature"));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!bizId || !deleting) return;
    setDeletingBusy(true);
    try {
      await featuresApi.delete(bizId, deleting.id);
      toast.success(`Feature "${deleting.name}" deleted`);
      setDeleting(null);
      void load();
    } catch (err) {
      toast.error(errorMessage(err, "Could not delete the feature"));
    } finally {
      setDeletingBusy(false);
    }
  };

  if (list === null) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Features"
        description="Things products are described by, like Color, Size or Brand. Give each product its values on the product page."
        view={list.length > 0 ? <ViewToggle mode={view} onChange={setView} /> : undefined}
        action={
          <div className="flex flex-wrap gap-2">
            <ImportButton kind="product-features" label="Upload product features" onDone={load} />
            <Button onClick={() => open("new")}><Plus className="h-4 w-4" /> Add feature</Button>
          </div>
        }
      />

      {list.length === 0 ? (
        <EmptyState
          title="No features yet"
          description="Add features like Color or Size, then set each product's value (e.g. Color: Red, Size: M)."
          action={<Button onClick={() => open("new")}><Plus className="h-4 w-4" /> Add feature</Button>}
        />
      ) : (
        <div className="overflow-hidden rounded-3xl border border-line/80 bg-surface shadow-card">
          <DataView mode={view}><Table>
            <TableHeader>
              <TableRow>
                <TableHead>Feature</TableHead>
                <TableHead>Products</TableHead>
                <TableHead>Values in use</TableHead>
                <TableHead className="w-24"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((f) => (
                <TableRow key={f.id}>
                  <TableCell className="font-medium text-ink">{f.name}</TableCell>
                  <TableCell>{f.productCount}</TableCell>
                  <TableCell>
                    {f.values.length === 0 ? <span className="text-ink-3">None yet</span> : (
                      <div className="flex flex-wrap justify-end gap-1 sm:justify-start">
                        {f.values.slice(0, MAX_VALUES_SHOWN).map((v) => <Badge key={v} variant="secondary">{v}</Badge>)}
                        {f.values.length > MAX_VALUES_SHOWN && <span className="text-xs text-ink-3">+{f.values.length - MAX_VALUES_SHOWN} more</span>}
                      </div>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon" className="h-8 w-8" aria-label={`Rename ${f.name}`} onClick={() => open(f)}>
                        <Pencil className="h-3.5 w-3.5" />
                      </Button>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-red-500 hover:text-red-600" aria-label={`Delete ${f.name}`} onClick={() => setDeleting(f)}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table></DataView>
        </div>
      )}

      <Dialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader><DialogTitle>{editing === "new" ? "Add feature" : "Rename feature"}</DialogTitle></DialogHeader>
          <form onSubmit={save} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="feature-name">Name</Label>
              <Input id="feature-name" autoFocus maxLength={80} placeholder="e.g. Color, Size, Brand, Material"
                value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEditing(null)}>Cancel</Button>
              <Button type="submit" disabled={saving || !name.trim()}>{saving ? "Saving…" : "Save"}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={`Delete "${deleting?.name ?? ""}"?`}
        description={deleting && deleting.productCount > 0
          ? `${deleting.productCount} product${deleting.productCount === 1 ? "" : "s"} will lose their ${deleting.name} value. The products themselves stay.`
          : "No products use it yet."}
        confirmLabel="Delete"
        onConfirm={remove}
        loading={deletingBusy}
      />
    </div>
  );
}
