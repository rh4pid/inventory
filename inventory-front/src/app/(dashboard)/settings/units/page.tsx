"use client";
import { DataView, ViewToggle, useViewMode } from "@/components/shared/DataView";
import { errorMessage } from "@/lib/utils";
import { confirmAction } from "@/context/ConfirmProvider";
import { useCallback, useEffect, useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { units as unitsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Unit } from "@/Types";
import { useCan } from "@/lib/access";

export default function UnitsSettingsPage() {
  const [view, setView] = useViewMode("units");
  const { activeBusiness } = useBusiness();
  const [list, setList] = useState<Unit[] | null>(null);
  const [editing, setEditing] = useState<Unit | "new" | null>(null);
  const [name, setName] = useState("");
  const [symbol, setSymbol] = useState("");
  const [saving, setSaving] = useState(false);
  const canEdit = useCan()("MANAGE_CATALOG");

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    try { setList(await unitsApi.list(activeBusiness.businessId)); }
    catch (err) { toast.error(errorMessage(err)); setList([]); }
  }, [activeBusiness]);

  useEffect(() => { load(); }, [load]);

  const open = (u: Unit | "new") => {
    setEditing(u);
    setName(u === "new" ? "" : u.name);
    setSymbol(u === "new" ? "" : u.symbol ?? "");
  };

  const save = async () => {
    if (!activeBusiness || !editing || !name.trim()) return;
    setSaving(true);
    try {
      const data = { name: name.trim(), symbol: symbol.trim() || undefined };
      if (editing === "new") await unitsApi.create(activeBusiness.businessId, data);
      else await unitsApi.update(activeBusiness.businessId, editing.id, data);
      toast.success("Unit saved");
      setEditing(null);
      load();
    } catch (err) { toast.error(errorMessage(err)); }
    finally { setSaving(false); }
  };

  const remove = async (u: Unit) => {
    if (!activeBusiness || !(await confirmAction({ title: `Delete the unit "${u.name}"?`, confirmLabel: "Delete", tone: "danger" }))) return;
    try {
      await unitsApi.delete(activeBusiness.businessId, u.id);
      toast.success("Unit deleted");
      load();
    } catch (err) { toast.error(errorMessage(err)); }
  };

  if (!list) return <PageLoader />;

  return (
    <div className="max-w-2xl">
      <PageHeader view={<ViewToggle mode={view} onChange={setView} />}
        title="Units of measure"
        description="The units products are counted in. Pack sizes (e.g. 1 Carton = 24 Pieces) are set on each product."
        action={canEdit && <Button size="sm" onClick={() => open("new")}><Plus className="h-4 w-4" /> Add unit</Button>}
      />

      <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-hidden">
        <DataView mode={view}><Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Symbol</TableHead>
              {canEdit && <TableHead className="w-24" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.map((u) => (
              <TableRow key={u.id}>
                <TableCell className="font-medium text-sm">{u.name}</TableCell>
                <TableCell className="text-sm text-gray-500">{u.symbol ?? "—"}</TableCell>
                {canEdit && (
                  <TableCell>
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="sm" onClick={() => open(u)} aria-label={`Edit ${u.name}`}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => remove(u)} aria-label={`Delete ${u.name}`}>
                        <Trash2 className="h-4 w-4 text-red-500" />
                      </Button>
                    </div>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table></DataView>
      </div>

      <Dialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editing === "new" ? "Add unit" : "Edit unit"}</DialogTitle></DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label>Name *</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Crate" />
            </div>
            <div className="space-y-1.5">
              <Label>Symbol</Label>
              <Input value={symbol} onChange={(e) => setSymbol(e.target.value)} placeholder="e.g. crt" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>Cancel</Button>
            <Button onClick={save} disabled={saving || !name.trim()}>{saving ? "Saving…" : "Save"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
