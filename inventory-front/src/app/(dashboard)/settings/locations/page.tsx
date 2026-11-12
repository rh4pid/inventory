"use client";
import { useCallback, useEffect, useState } from "react";
import { Pencil, Plus, Trash2, Users, X } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { shops as shopsApi, employees as employeesApi } from "@/lib/api";
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
import type { BusinessUser, LocationStaff, LocationType, Shop } from "@/Types";

const MANAGER_ROLES = ["OWNER", "ADMIN", "MANAGER"];

export default function LocationsSettingsPage() {
  const { activeBusiness } = useBusiness();
  const [list, setList] = useState<Shop[] | null>(null);
  const [editing, setEditing] = useState<Shop | "new" | null>(null);
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [type, setType] = useState<LocationType>("SHOP");
  const [saving, setSaving] = useState(false);
  const canEdit = MANAGER_ROLES.includes(activeBusiness?.role ?? "");
  const [staffFor, setStaffFor] = useState<Shop | null>(null);

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
                {canEdit && <TableHead className="w-32" />}
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
                        <Button variant="ghost" size="sm" onClick={() => setStaffFor(s)} aria-label={`Staff at ${s.shopName}`}>
                          <Users className="h-4 w-4" />
                        </Button>
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

      {activeBusiness && staffFor && (
        <LocationStaffDialog businessId={activeBusiness.businessId} shop={staffFor} onClose={() => setStaffFor(null)} />
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

/** Who works at a location. Owners, admins and managers can use every location without being listed. */
function LocationStaffDialog({ businessId, shop, onClose }: { businessId: number; shop: Shop; onClose: () => void }) {
  const [assigned, setAssigned] = useState<LocationStaff[] | null>(null);
  const [members, setMembers] = useState<BusinessUser[]>([]);
  const [pick, setPick] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [a, m] = await Promise.all([
        shopsApi.staff(businessId, shop.id),
        employeesApi.listStaff(businessId, { size: 200 }),
      ]);
      setAssigned(a);
      setMembers(m.content);
    } catch (err: any) { toast.error(err.message); setAssigned([]); }
  }, [businessId, shop.id]);

  useEffect(() => { load(); }, [load]);

  const assignedIds = new Set((assigned ?? []).map((a) => a.businessUserId));
  const candidates = members.filter((m) => !MANAGER_ROLES.includes(m.roles) && !assignedIds.has(m.id));
  const nameOf = (m: BusinessUser) =>
    [m.user?.firstName, m.user?.lastName].filter(Boolean).join(" ") || m.user?.email || `#${m.id}`;

  const add = async () => {
    if (!pick) return;
    setBusy(true);
    try {
      await shopsApi.assignStaff(businessId, shop.id, Number(pick));
      setPick("");
      await load();
    } catch (err: any) { toast.error(err.message); }
    finally { setBusy(false); }
  };

  const remove = async (a: LocationStaff) => {
    setBusy(true);
    try {
      await shopsApi.unassignStaff(businessId, shop.id, a.id);
      await load();
    } catch (err: any) { toast.error(err.message); }
    finally { setBusy(false); }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>Staff at {shop.shopName}</DialogTitle></DialogHeader>
        <p className="text-sm text-gray-500">
          Staff can only sell, receive and count stock at the locations they are assigned to.
          Owners, admins and managers can use every location.
        </p>
        {assigned === null ? (
          <p className="py-4 text-sm text-gray-500">Loading…</p>
        ) : assigned.length === 0 ? (
          <p className="py-4 text-sm text-gray-500">No staff assigned yet.</p>
        ) : (
          <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200">
            {assigned.map((a) => (
              <li key={a.id} className="flex items-center justify-between px-3 py-2 text-sm">
                <div>
                  <div className="font-medium">{a.name}</div>
                  <div className="text-xs text-gray-500">{a.email} · {a.role}</div>
                </div>
                <Button variant="ghost" size="sm" disabled={busy} onClick={() => remove(a)} aria-label={`Remove ${a.name}`}>
                  <X className="h-4 w-4" />
                </Button>
              </li>
            ))}
          </ul>
        )}
        <div className="flex gap-2">
          <Select value={pick} onValueChange={setPick} disabled={candidates.length === 0}>
            <SelectTrigger className="flex-1">
              <SelectValue placeholder={candidates.length === 0 ? "Everyone is already assigned" : "Choose a staff member"} />
            </SelectTrigger>
            <SelectContent>
              {candidates.map((m) => (
                <SelectItem key={m.id} value={String(m.id)}>{nameOf(m)} ({m.roles})</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button onClick={add} disabled={!pick || busy}><Plus className="h-4 w-4" /> Assign</Button>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Done</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
