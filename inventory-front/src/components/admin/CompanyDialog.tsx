"use client";
import { confirmAction } from "@/context/ConfirmProvider";
import { useEffect, useState } from "react";
import { KeyRound, Pause, Play, Plus, UserRound } from "lucide-react";
import { toast } from "sonner";
import { admin as adminApi } from "@/lib/api";
import { cn, errorMessage } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { CredentialsCard } from "@/components/account/CredentialsCard";
import type { Company, HandoverCredentials } from "@/Types";

const when = (iso?: string) => (iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "—");

/** One company: details, usage, admins (add / reset password) and suspend / reactivate. */
export function CompanyDialog({ company: initial, onClose, onChanged }: {
  company: Company;
  onClose: () => void;
  onChanged: (c: Company) => void;
}) {
  const [c, setC] = useState(initial);
  const [form, setForm] = useState({ name: initial.name, email: initial.email ?? "", contact: initial.contact ?? "" });
  const [busy, setBusy] = useState(false);
  const [handover, setHandover] = useState<HandoverCredentials | null>(null);
  const [adding, setAdding] = useState(false);
  const [person, setPerson] = useState({ firstName: "", lastName: "", email: "", phone: "" });
  const [confirmSuspend, setConfirmSuspend] = useState(false);

  useEffect(() => { setC(initial); }, [initial]);

  const apply = (next: Company) => { setC(next); onChanged(next); };
  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    try { await fn(); } catch (err) { toast.error(errorMessage(err, "Something went wrong")); } finally { setBusy(false); }
  };

  const dirty = form.name !== c.name || form.email !== (c.email ?? "") || form.contact !== (c.contact ?? "");
  const save = () => run(async () => {
    apply(await adminApi.updateCompany(c.id, form));
    toast.success("Company details saved");
  });

  const addAdmin = (e: React.FormEvent) => {
    e.preventDefault();
    run(async () => {
      const r = await adminApi.addAdmin(c.id, { ...person, phone: person.phone || undefined });
      apply(r.company);
      setHandover(r.credentials);
      setAdding(false);
      setPerson({ firstName: "", lastName: "", email: "", phone: "" });
    });
  };

  const reset = async (membershipId: number, name: string) => {
    if (!(await confirmAction({ title: `Reset ${name}'s password?`, description: "They get a new temporary password. Their current password stops working and they're signed out.", confirmLabel: "Reset password", tone: "danger" }))) return;
    run(async () => {
      const r = await adminApi.resetAdminPassword(c.id, membershipId);
      apply(r.company);
      setHandover(r.credentials);
    });
  };

  const toggleSuspend = () => run(async () => {
    const next = c.suspended ? await adminApi.reactivate(c.id) : await adminApi.suspend(c.id);
    apply(next);
    setConfirmSuspend(false);
    toast.success(next.suspended ? `${c.name} suspended` : `${c.name} reactivated`);
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex flex-wrap items-center gap-2">
            {c.name}
            <span className="font-mono text-xs font-normal text-ink-3">{c.code}</span>
            <span className={cn("rounded-lg px-2 py-0.5 text-xs font-semibold", c.suspended ? "bg-bad-soft text-bad" : "bg-ok-soft text-ok")}>
              {c.suspended ? "Suspended" : "Active"}
            </span>
          </DialogTitle>
        </DialogHeader>
        <p className="-mt-2 text-sm text-ink-3">{c.businessTypeLabel ?? "—"} · created {when(c.createdAt)}</p>

        {/* Usage */}
        <dl className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {[
            { k: "Locations", v: c.locations },
            { k: "People", v: c.members },
            { k: "Products", v: c.products },
            { k: "Last activity", v: when(c.lastActivity) },
          ].map((s) => (
            <div key={s.k} className="rounded-2xl bg-surface-2 px-3 py-2.5">
              <dt className="text-xs text-ink-3">{s.k}</dt>
              <dd className="text-lg font-semibold text-ink tabular-nums">{s.v}</dd>
            </div>
          ))}
        </dl>

        {handover && (
          <div className="mt-5">
            <CredentialsCard {...handover} title="Admin sign-in to hand over" />
            <button className="mt-2 text-xs text-ink-3 hover:text-ink" onClick={() => setHandover(null)}>Hide</button>
          </div>
        )}

        {/* Admins */}
        <section className="mt-6">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-ink">Admins</h3>
            {!adding && <Button size="sm" variant="outline" onClick={() => setAdding(true)}><Plus className="h-4 w-4" /> Add admin</Button>}
          </div>
          <ul className="divide-y divide-line rounded-2xl border border-line">
            {c.admins.map((a) => (
              <li key={a.membershipId} className="flex items-center gap-3 px-3 py-2.5">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-ink-3"><UserRound className="h-4 w-4" /></span>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium text-ink">{a.name} <span className="text-xs font-normal text-ink-3">· {a.role === "OWNER" ? "Owner" : "Admin"}</span></div>
                  <div className="truncate text-xs text-ink-3">
                    {a.email}{a.pendingFirstSignIn && <span className="ml-1.5 rounded bg-accent-soft px-1.5 py-0.5 text-[11px] font-medium text-accent-deep">Hasn&apos;t signed in yet</span>}
                  </div>
                </div>
                <Button size="sm" variant="ghost" disabled={busy} onClick={() => reset(a.membershipId, a.name)}>
                  <KeyRound className="h-4 w-4" /> Reset password
                </Button>
              </li>
            ))}
            {c.admins.length === 0 && <li className="px-3 py-3 text-sm text-ink-3">No admin. Add one so the company can be used.</li>}
          </ul>

          {adding && (
            <form onSubmit={addAdmin} className="mt-3 space-y-3 rounded-2xl border border-line bg-surface-2/60 p-3">
              <div className="grid gap-3 sm:grid-cols-2">
                <Input aria-label="First name" placeholder="First name *" value={person.firstName} onChange={(e) => setPerson((p) => ({ ...p, firstName: e.target.value }))} />
                <Input aria-label="Last name" placeholder="Last name *" value={person.lastName} onChange={(e) => setPerson((p) => ({ ...p, lastName: e.target.value }))} />
                <Input aria-label="Email" type="email" placeholder="Email (sign-in) *" value={person.email} onChange={(e) => setPerson((p) => ({ ...p, email: e.target.value }))} />
                <Input aria-label="Phone" placeholder="Phone" value={person.phone} onChange={(e) => setPerson((p) => ({ ...p, phone: e.target.value }))} />
              </div>
              <div className="flex justify-end gap-2">
                <Button type="button" size="sm" variant="ghost" onClick={() => setAdding(false)}>Cancel</Button>
                <Button type="submit" size="sm" disabled={busy || !person.email.trim() || !person.firstName.trim() || !person.lastName.trim()}>Add admin</Button>
              </div>
            </form>
          )}
        </section>

        {/* Details */}
        <section className="mt-6 space-y-3">
          <h3 className="text-sm font-semibold text-ink">Details</h3>
          <div className="space-y-1.5">
            <Label htmlFor="co-name">Company name</Label>
            <Input id="co-name" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="co-email">Email</Label>
              <Input id="co-email" type="email" value={form.email} onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="co-contact">Phone</Label>
              <Input id="co-contact" value={form.contact} onChange={(e) => setForm((f) => ({ ...f, contact: e.target.value }))} />
            </div>
          </div>
          {dirty && (
            <div className="flex justify-end">
              <Button size="sm" onClick={save} disabled={busy || !form.name.trim()}>Save details</Button>
            </div>
          )}
        </section>

        {/* Suspend */}
        <section className={cn("mt-6 rounded-2xl border p-4", c.suspended ? "border-brand/20 bg-brand-soft" : "border-accent/25 bg-accent-soft")}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="min-w-0">
              <h3 className="text-sm font-semibold text-ink">{c.suspended ? "Company is suspended" : "Suspend company"}</h3>
              <p className="text-xs text-ink-2">
                {c.suspended
                  ? "Nobody in this company can sign in. Their data is kept."
                  : "Everyone in the company is signed out of it and can't sign in until you reactivate it. Data is kept."}
              </p>
            </div>
            {c.suspended ? (
              <Button size="sm" onClick={toggleSuspend} disabled={busy}><Play className="h-4 w-4" /> Reactivate</Button>
            ) : confirmSuspend ? (
              <div className="flex gap-2">
                <Button size="sm" variant="ghost" onClick={() => setConfirmSuspend(false)}>Cancel</Button>
                <Button size="sm" variant="accent" onClick={toggleSuspend} disabled={busy}>Yes, suspend</Button>
              </div>
            ) : (
              <Button size="sm" variant="outline" onClick={() => setConfirmSuspend(true)}><Pause className="h-4 w-4" /> Suspend</Button>
            )}
          </div>
        </section>
      </DialogContent>
    </Dialog>
  );
}
