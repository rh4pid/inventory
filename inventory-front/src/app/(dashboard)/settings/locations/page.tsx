"use client";
import { useCallback, useEffect, useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { shops as shopsApi } from "@/lib/api";
import { LOCATION_TYPES, locationTypeLabel } from "@/constants/Inventory";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { LocationType, Shop } from "@/Types";

export default function LocationsSettingsPage() {
  const { activeBusiness } = useBusiness();
  const [list, setList] = useState<Shop[] | null>(null);
  const [editing, setEditing] = useState<Shop | "new" | null>(null);
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [type, setType] = useState<LocationType>("SHOP");
  const [saving, setSaving] = useState(false);
  const canEdit = ["OWNER", "ADMIN", "MANAGER"].includes(activeBusiness?.role ?? "");

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    try { setList((await shopsApi.list(activeBusiness.businessId)).content); }
    catch (err: any) { toast.error(err.message); setList([]); }
  }, [activeBusiness]);

  useEffect(() => { load(); }, [load]);

  const open = (s: Shop | "new") => {
    setEditing(s);
    setName(s === "new" ? "" : s.shopName);
    setAddress(s === "new" ? "" : s.shopLocation ?? "");
    setType(s === "new" ? "SHOP" : s.locationType ?? "SHOP");
  };

  const save = async () => {
    if (!activeBusiness || !editing || !name.trim()) return;
    setSaving(true);
    try {
      const data = {
        businessId: activeBusiness.businessId,
        shopName: name.trim(),
        shopLocation: address.trim() || undefined,
        locationType: type,
      };
      if (editing === "new") await shopsApi.create(data);
      else await shopsApi.update(editing.id, data);
      toast.success("Location saved");
      setEditing(null);
      load();
    } catch (err: any) { toast.error(err.message); }
    finally { setSaving(false); }
  };

  const remove = async (s: Shop) => {
    if (!window.confirm(`Remove "${s.shopName}"? Its stock history is kept.`)) return;
    try {
      await shopsApi.delete(s.id);
      toast.success("Location removed");
      load();
    } catch (err: any) { toast.error(err.message); }
  };

  if (!list) return <PageLoader />;

  return (
    <div className="max-w-3xl">
      <PageHeader
        title="Locations"
        description="Every place you hold stock: shops, warehouses, storerooms, vans…"
        action={canEdit && <Button size="sm" onClick={() => open("new")}><Plus className="h-4 w-4" /> Add location</Button>}
      />

      {list.length === 0 ? (
        <EmptyState title="No locations yet" description="Add your first shop or warehouse to start tracking stock." />
      ) : (
        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Address</TableHead>
                <TableHead>Code</TableHead>
                {canEdit && <TableHead className="w-24" />}
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((s) => (
                <TableRow key={s.id}>
                  <TableCell className="font-medium text-sm">{s.shopName}</TableCell>
                  <TableCell><Badge variant="secondary">{locationTypeLabel(s.locationType)}</Badge></TableCell>
                  <TableCell className="text-sm text-gray-500">{s.shopLocation ?? "—"}</TableCell>
                  <TableCell className="font-mono text-xs text-gray-500">{s.shopCode}</TableCell>
                  {canEdit && (
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="sm" onClick={() => open(s)} aria-label={`Edit ${s.shopName}`}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => remove(s)} aria-label={`Remove ${s.shopName}`}>
                          <Trash2 className="h-4 w-4 text-red-500" />
                        </Button>
                      </div>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editing === "new" ? "Add location" : "Edit location"}</DialogTitle></DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label>Name *</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Main Warehouse" />
            </div>
            <div className="space-y-1.5">
              <Label>Type</Label>
              <Select value={type} onValueChange={(v) => setType(v as LocationType)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {LOCATION_TYPES.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Address</Label>
              <Input value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Optional" />
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
