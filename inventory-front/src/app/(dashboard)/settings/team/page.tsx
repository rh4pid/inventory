"use client";
import { useCallback, useEffect, useState } from "react";
import { KeyRound, MoreHorizontal, Pencil, Plus, UserMinus } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { useWorkspace } from "@/context/WorkspaceContext";
import { shops as shopsApi, team as teamApi } from "@/lib/api";
import { cn } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { Monogram } from "@/components/dashboard/Panel";
import { CredentialsCard } from "@/components/account/CredentialsCard";
import type { Shop, TeamAdded, TeamList, TeamMember, UserRole } from "@/Types";

const ROLE: Record<string, { label: string; hint: string; everywhere: boolean }> = {
  OWNER:   { label: "Owner",   hint: "Everything, including settings, accounts, payroll and admins", everywhere: true },
  ADMIN:   { label: "Admin",   hint: "Everything, including settings, accounts, payroll and other admins", everywhere: true },
  MANAGER: { label: "Manager", hint: "Day-to-day at every location: products and prices, stock, purchases, approvals, returns and voids, front-line staff. Not settings, accounts or payroll", everywhere: true },
  CASHIER: { label: "Cashier", hint: "Sells and takes customer payments, adds and edits customers, asks for stock to be written off. Sees prices, not costs", everywhere: false },
  STAFF:   { label: "Staff",   hint: "Receives deliveries, stock in, counts and transfers, asks for adjustments. Doesn't sell or see costs", everywhere: false },
  DRIVER:  { label: "Driver",  hint: "Moves stock between their locations and sees stock levels there", everywhere: false },
  SHOPKEEP:{ label: "Shopkeeper", hint: "Legacy role", everywhere: false },
};

function LocationPicker({ locations, value, onChange }: { locations: Shop[]; value: number[]; onChange: (v: number[]) => void }) {
  if (locations.length === 0) {
    return <p className="text-sm text-ink-3">No locations yet. Add one under Settings → Locations.</p>;
  }
  return (
    <div className="flex flex-wrap gap-2">
      {locations.map((s) => {
        const on = value.includes(s.id);
        return (
          <button key={s.id} type="button" aria-pressed={on}
            onClick={() => onChange(on ? value.filter((x) => x !== s.id) : [...value, s.id])}
            className={cn("rounded-xl border px-3 py-1.5 text-sm transition-colors",
              on ? "border-brand bg-brand-soft font-medium text-brand-deep" : "border-line bg-surface text-ink-2 hover:bg-surface-2")}>
            {s.shopName}
          </button>
        );
      })}
    </div>
  );
}

export default function TeamPage() {
  const { activeBusiness } = useBusiness();
  const { refreshLocations } = useWorkspace();
  const bizId = activeBusiness?.businessId;
  const [data, setData] = useState<TeamList | null>(null);
  const [locations, setLocations] = useState<Shop[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<TeamMember | null>(null);
  const [handover, setHandover] = useState<TeamAdded | null>(null);

  const load = useCallback(async () => {
    if (!bizId) return;
    try {
      const [t, s] = await Promise.all([teamApi.list(bizId), shopsApi.list(bizId)]);
      setData(t);
      setLocations(s.content);
      setError(null);
    } catch (err: any) { setError(err.message ?? "Could not load the team"); }
  }, [bizId]);

  useEffect(() => { load(); }, [load]);

  if (error) return <EmptyState title="Team" description={error} />;
  if (!data || !bizId) return <PageLoader />;

  const resetPassword = async (m: TeamMember) => {
    if (!window.confirm(`Give ${m.name} a new temporary password? Their current password stops working.`)) return;
    try {
      const r = await teamApi.resetPassword(bizId, m.id);
      setHandover(r);
      load();
    } catch (err: any) { toast.error(err.message); }
  };

  const remove = async (m: TeamMember) => {
    if (!window.confirm(`Remove ${m.name} from ${activeBusiness?.businessName}? They lose access straight away. Their past sales and stock records are kept.`)) return;
    try {
      await teamApi.remove(bizId, m.id);
      toast.success(`${m.name} removed`);
      load();
    } catch (err: any) { toast.error(err.message); }
  };

  return (
    <div className="max-w-5xl">
      <PageHeader
        title="Team"
        description="Create sign-ins for your people, choose what they do and where they work."
        action={data.rolesYouCanAssign.length > 0 && <Button onClick={() => setAdding(true)}><Plus className="h-4 w-4" /> Add person</Button>}
      />

      <div className="overflow-hidden rounded-4xl border border-line/80 bg-surface shadow-card">
        <ul className="divide-y divide-line/70">
          {data.members.map((m) => (
            <li key={m.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center">
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <Monogram name={m.name} />
                <div className="min-w-0">
                  <div className="truncate font-medium text-ink">
                    {m.name} {m.you && <span className="ml-1 rounded-md bg-surface-2 px-1.5 py-0.5 text-[11px] font-medium text-ink-3">You</span>}
                  </div>
                  <div className="truncate text-xs text-ink-3">
                    {m.email}{m.phone ? ` · ${m.phone}` : ""}
                    {m.pendingFirstSignIn && <span className="ml-1.5 rounded bg-accent-soft px-1.5 py-0.5 text-[11px] font-medium text-accent-deep">Hasn&apos;t signed in yet</span>}
                  </div>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2 sm:w-[22rem] sm:justify-end">
                <span className="rounded-lg bg-brand-soft px-2 py-1 text-xs font-semibold text-brand-deep" title={ROLE[m.role]?.hint}>
                  {ROLE[m.role]?.label ?? m.role}
                </span>
                {ROLE[m.role]?.everywhere ? (
                  <span className="text-xs text-ink-3">All locations</span>
                ) : m.locations.length ? (
                  m.locations.map((l) => <span key={l.id} className="rounded-lg bg-surface-2 px-2 py-1 text-xs text-ink-2">{l.name}</span>)
                ) : (
                  <span className="text-xs font-medium text-accent-deep">No location: can&apos;t work yet</span>
                )}
              </div>
              <div className="sm:w-10 sm:text-right">
                {m.canManage && (
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <button aria-label={`Actions for ${m.name}`} className="inline-flex h-9 w-9 items-center justify-center rounded-xl text-ink-3 hover:bg-surface-2 hover:text-ink">
                        <MoreHorizontal className="h-5 w-5" />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="w-52">
                      <DropdownMenuItem onClick={() => setEditing(m)}><Pencil className="mr-2 h-4 w-4" /> Role & locations</DropdownMenuItem>
                      <DropdownMenuItem onClick={() => resetPassword(m)}><KeyRound className="mr-2 h-4 w-4" /> Reset password</DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem onClick={() => remove(m)} className="text-red-600 focus:bg-red-50 focus:text-red-600">
                        <UserMinus className="mr-2 h-4 w-4" /> Remove from company
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>

      <AddPersonDialog
        open={adding} onClose={() => setAdding(false)} businessId={bizId}
        roles={data.rolesYouCanAssign} locations={locations}
        onAdded={(r) => { setAdding(false); setHandover(r); load(); refreshLocations(); }}
      />
      {editing && (
        <EditPersonDialog member={editing} businessId={bizId} roles={data.rolesYouCanAssign} locations={locations}
          onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />
      )}
      <Dialog open={!!handover} onOpenChange={(o) => !o && setHandover(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader><DialogTitle>{handover?.temporaryPassword ? "Sign-in ready" : "Added to the team"}</DialogTitle></DialogHeader>
          {handover && (
            <CredentialsCard name={handover.member.name} email={handover.member.email}
              temporaryPassword={handover.temporaryPassword} existingAccount={handover.existingAccount} />
          )}
          <DialogFooter><Button onClick={() => setHandover(null)}>Done</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function RoleSelect({ roles, value, onChange }: { roles: UserRole[]; value: UserRole; onChange: (r: UserRole) => void }) {
  return (
    <div className="space-y-1.5">
      <Label>Role</Label>
      <Select value={value} onValueChange={(v) => onChange(v as UserRole)}>
        <SelectTrigger><SelectValue /></SelectTrigger>
        <SelectContent>
          {roles.map((r) => <SelectItem key={r} value={r}>{ROLE[r]?.label ?? r}</SelectItem>)}
        </SelectContent>
      </Select>
      <p className="text-xs text-ink-3">{ROLE[value]?.hint}</p>
    </div>
  );
}

function AddPersonDialog({ open, onClose, businessId, roles, locations, onAdded }: {
  open: boolean; onClose: () => void; businessId: number; roles: UserRole[]; locations: Shop[];
  onAdded: (r: TeamAdded) => void;
}) {
  const blank = { firstName: "", lastName: "", email: "", phone: "" };
  const [p, setP] = useState(blank);
  const [role, setRole] = useState<UserRole>("CASHIER");
  const [shopIds, setShopIds] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setP(blank);
    setRole(roles.includes("CASHIER") ? "CASHIER" : roles[0]);
    setShopIds(locations.length === 1 ? [locations[0].id] : []);
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const everywhere = ROLE[role]?.everywhere;
  const valid = p.firstName.trim() && p.lastName.trim() && p.email.trim() && (everywhere || shopIds.length > 0);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    setBusy(true);
    try {
      onAdded(await teamApi.add(businessId, {
        firstName: p.firstName.trim(), lastName: p.lastName.trim(), email: p.email.trim(),
        phone: p.phone.trim() || undefined, role, shopIds: everywhere ? [] : shopIds,
      }));
    } catch (err: any) { toast.error(err.message ?? "Could not add this person"); }
    finally { setBusy(false); }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader><DialogTitle>Add person</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5"><Label htmlFor="ap-first">First name *</Label><Input id="ap-first" value={p.firstName} onChange={(e) => setP((x) => ({ ...x, firstName: e.target.value }))} /></div>
            <div className="space-y-1.5"><Label htmlFor="ap-last">Last name *</Label><Input id="ap-last" value={p.lastName} onChange={(e) => setP((x) => ({ ...x, lastName: e.target.value }))} /></div>
            <div className="space-y-1.5"><Label htmlFor="ap-email">Email (sign-in) *</Label><Input id="ap-email" type="email" value={p.email} onChange={(e) => setP((x) => ({ ...x, email: e.target.value }))} /></div>
            <div className="space-y-1.5"><Label htmlFor="ap-phone">Phone</Label><Input id="ap-phone" value={p.phone} onChange={(e) => setP((x) => ({ ...x, phone: e.target.value }))} placeholder="Optional" /></div>
          </div>
          <RoleSelect roles={roles} value={role} onChange={setRole} />
          <div className="space-y-1.5">
            <Label>Works at {everywhere ? "" : "*"}</Label>
            {everywhere
              ? <p className="text-sm text-ink-3">{ROLE[role].label}s can use every location.</p>
              : <LocationPicker locations={locations} value={shopIds} onChange={setShopIds} />}
          </div>
          <p className="text-xs text-ink-3">They get a temporary password to sign in with and choose their own at first sign-in.</p>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={!valid || busy}>{busy ? "Adding…" : "Add person"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function EditPersonDialog({ member, businessId, roles, locations, onClose, onSaved }: {
  member: TeamMember; businessId: number; roles: UserRole[]; locations: Shop[]; onClose: () => void; onSaved: () => void;
}) {
  const [role, setRole] = useState<UserRole>(member.role);
  const [shopIds, setShopIds] = useState<number[]>(member.locations.map((l) => l.id));
  const [busy, setBusy] = useState(false);
  const everywhere = ROLE[role]?.everywhere;
  const options = roles.includes(member.role) ? roles : [member.role, ...roles];

  const save = async () => {
    setBusy(true);
    try {
      await teamApi.update(businessId, member.id, { role, shopIds: everywhere ? [] : shopIds });
      toast.success(`${member.name} updated`);
      onSaved();
    } catch (err: any) { toast.error(err.message); }
    finally { setBusy(false); }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader><DialogTitle>{member.name}</DialogTitle></DialogHeader>
        <div className="space-y-4">
          <RoleSelect roles={options} value={role} onChange={setRole} />
          <div className="space-y-1.5">
            <Label>Works at</Label>
            {everywhere
              ? <p className="text-sm text-ink-3">{ROLE[role].label}s can use every location.</p>
              : <LocationPicker locations={locations} value={shopIds} onChange={setShopIds} />}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={save} disabled={busy || (!everywhere && shopIds.length === 0)}>{busy ? "Saving…" : "Save"}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
