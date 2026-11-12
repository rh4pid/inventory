"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Building2, MapPin, Package, Plus, RefreshCw, Search, Users } from "lucide-react";
import { toast } from "sonner";
import { admin as adminApi } from "@/lib/api";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/shared/EmptyState";
import { Panel } from "@/components/dashboard/Panel";
import { CreateCompanyDialog } from "@/components/admin/CreateCompanyDialog";
import { CompanyDialog } from "@/components/admin/CompanyDialog";
import type { Company } from "@/Types";

type Filter = "all" | "active" | "suspended";

const ago = (iso?: string) => {
  if (!iso) return "No activity yet";
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  return days <= 0 ? "Active today" : days === 1 ? "Active yesterday" : `Active ${days} days ago`;
};

export default function AdminConsolePage() {
  const [companies, setCompanies] = useState<Company[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<Filter>("all");
  const [creating, setCreating] = useState(false);
  const [opened, setOpened] = useState<Company | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try { setCompanies(await adminApi.companies()); }
    catch (err: any) { setError(err.message ?? "Could not load companies"); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const shown = useMemo(() => {
    const term = q.trim().toLowerCase();
    return (companies ?? []).filter((c) =>
      (filter === "all" || (filter === "suspended") === c.suspended) &&
      (!term || c.name.toLowerCase().includes(term) || c.code.toLowerCase().includes(term) ||
        c.admins.some((a) => a.email.toLowerCase().includes(term) || a.name.toLowerCase().includes(term))));
  }, [companies, q, filter]);

  if (error) {
    return <EmptyState title="Couldn't load companies" description={error}
      action={<Button variant="outline" onClick={load}><RefreshCw className="h-4 w-4" /> Try again</Button>} />;
  }

  const all = companies ?? [];
  const totals = {
    companies: all.length,
    active: all.filter((c) => !c.suspended).length,
    people: all.reduce((s, c) => s + c.members, 0),
    locations: all.reduce((s, c) => s + c.locations, 0),
  };
  const replace = (c: Company) => {
    setCompanies((list) => (list ?? []).map((x) => (x.id === c.id ? c : x)));
    setOpened((o) => (o && o.id === c.id ? c : o));
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-[26px] font-semibold tracking-tight text-ink md:text-[28px]">Companies</h1>
          <p className="mt-1 text-sm text-ink-3">Create companies and their admins. Admins then set up branches, team and products.</p>
        </div>
        <Button variant="accent" onClick={() => setCreating(true)}><Plus className="h-4 w-4" /> New company</Button>
      </div>

      {/* Headline numbers, in the brand cards */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Panel tone="blue" className="p-5 md:p-5">
          <p className="text-sm text-white/85">Companies</p>
          <p className="mt-1 text-3xl font-semibold tabular-nums">{companies ? totals.companies : "–"}</p>
          <p className="text-xs text-white/75">{companies ? `${totals.active} active · ${totals.companies - totals.active} suspended` : " "}</p>
        </Panel>
        <Panel tone="orange" className="p-5 md:p-5">
          <p className="text-sm text-ink/80">People</p>
          <p className="mt-1 text-3xl font-semibold tabular-nums">{companies ? totals.people : "–"}</p>
          <p className="text-xs text-ink/70">across all companies</p>
        </Panel>
        <Panel className="p-5 md:p-5">
          <p className="text-sm text-ink-3">Locations</p>
          <p className="mt-1 text-3xl font-semibold text-ink tabular-nums">{companies ? totals.locations : "–"}</p>
          <p className="text-xs text-ink-3">branches, warehouses…</p>
        </Panel>
        <Panel className="p-5 md:p-5">
          <p className="text-sm text-ink-3">Waiting on first sign-in</p>
          <p className="mt-1 text-3xl font-semibold text-ink tabular-nums">
            {companies ? all.reduce((s, c) => s + c.admins.filter((a) => a.pendingFirstSignIn).length, 0) : "–"}
          </p>
          <p className="text-xs text-ink-3">admins with a temporary password</p>
        </Panel>
      </div>

      {/* Filters */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <label className="relative block sm:w-80">
          <span className="sr-only">Search companies</span>
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-3" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name, code or admin…"
            className="h-11 w-full rounded-2xl border border-line bg-surface pl-10 pr-3 text-sm text-ink outline-none placeholder:text-ink-3 focus:border-brand focus:ring-2 focus:ring-brand/30" />
        </label>
        <div className="inline-flex h-11 rounded-2xl bg-surface-2 p-1" role="tablist" aria-label="Status">
          {(["all", "active", "suspended"] as Filter[]).map((f) => (
            <button key={f} role="tab" aria-selected={filter === f} onClick={() => setFilter(f)}
              className={cn("rounded-xl px-4 text-sm font-medium capitalize", filter === f ? "bg-surface text-ink shadow-card" : "text-ink-3 hover:text-ink")}>
              {f}
            </button>
          ))}
        </div>
      </div>

      {/* Companies */}
      {!companies ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2].map((i) => <div key={i} className="h-52 animate-pulse rounded-4xl bg-surface-2" />)}
        </div>
      ) : shown.length === 0 ? (
        <Panel>
          <EmptyState
            title={all.length === 0 ? "No companies yet" : "No companies match"}
            description={all.length === 0 ? "Create the first company and its admin to get started." : "Try another search or filter."}
            icon={<Building2 className="h-12 w-12 text-ink-3/40" />}
            action={all.length === 0 && <Button variant="accent" onClick={() => setCreating(true)}><Plus className="h-4 w-4" /> New company</Button>}
          />
        </Panel>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {shown.map((c) => {
            const lead = c.admins[0];
            return (
              <button key={c.id} onClick={() => setOpened(c)}
                className="group rounded-4xl border border-line/80 bg-surface p-5 text-left shadow-card transition-shadow hover:shadow-float focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="truncate text-lg font-semibold text-ink">{c.name}</div>
                    <div className="text-xs text-ink-3"><span className="font-mono">{c.code}</span> · {c.businessTypeLabel ?? "—"}</div>
                  </div>
                  <span className={cn("shrink-0 rounded-lg px-2 py-0.5 text-xs font-semibold", c.suspended ? "bg-accent text-white" : "bg-brand-soft text-brand-deep")}>
                    {c.suspended ? "Suspended" : "Active"}
                  </span>
                </div>
                <div className="mt-4 rounded-2xl bg-surface-2/70 px-3 py-2.5 text-sm">
                  <div className="text-xs text-ink-3">Admin</div>
                  {lead ? (
                    <div className="truncate font-medium text-ink">
                      {lead.name}{c.admins.length > 1 && <span className="font-normal text-ink-3"> +{c.admins.length - 1}</span>}
                      {lead.pendingFirstSignIn && <span className="ml-1.5 text-xs font-normal text-accent-deep">· not signed in yet</span>}
                    </div>
                  ) : <div className="text-accent-deep">No admin</div>}
                </div>
                <div className="mt-4 flex items-center gap-4 text-sm text-ink-2">
                  <span className="flex items-center gap-1.5" title="Locations"><MapPin className="h-4 w-4 text-ink-3" />{c.locations}</span>
                  <span className="flex items-center gap-1.5" title="People"><Users className="h-4 w-4 text-ink-3" />{c.members}</span>
                  <span className="flex items-center gap-1.5" title="Products"><Package className="h-4 w-4 text-ink-3" />{c.products}</span>
                  <span className="ml-auto text-xs text-ink-3">{ago(c.lastActivity)}</span>
                </div>
              </button>
            );
          })}
        </div>
      )}

      <CreateCompanyDialog open={creating} onClose={() => setCreating(false)} onCreated={() => load().catch(() => toast.error("Refresh failed"))} />
      {opened && <CompanyDialog company={opened} onClose={() => setOpened(null)} onChanged={replace} />}
    </div>
  );
}
