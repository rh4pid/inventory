"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Check, ChevronRight, X } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { useWorkspace } from "@/context/WorkspaceContext";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { accounts as accountsApi, products as productsApi, team as teamApi } from "@/lib/api";
import { cn } from "@/lib/utils";
import { Panel } from "./Panel";
import { useCan } from "@/lib/access";

interface Step { key: string; title: string; hint: string; href: string; done: boolean }

/**
 * For a new company's owner/admin: what to do to get the system ready. Each step ticks itself
 * off from real data; the card disappears once everything is done (or is dismissed).
 */
export function SetupChecklist({ hasStock }: { hasStock: boolean }) {
  const { activeBusiness } = useBusiness();
  const { locations } = useWorkspace();
  const { settings } = useBusinessSettings();
  const bizId = activeBusiness?.businessId;
  const isAdmin = useCan()("MANAGE_SETTINGS");
  const key = `setup-dismissed:${bizId}`;
  const [dismissed, setDismissed] = useState(true);
  const [facts, setFacts] = useState<{ teamEmails: string[]; products: number; accounts: number } | null>(null);

  useEffect(() => {
    try { setDismissed(localStorage.getItem(key) === "1"); } catch { setDismissed(false); }
  }, [key]);

  useEffect(() => {
    if (!bizId || !isAdmin) return;
    Promise.all([
      teamApi.list(bizId).then((t) => t.members.map((m) => m.email.toLowerCase())).catch(() => []),
      productsApi.list(bizId, { size: 1 }).then((p) => p.totalElements).catch(() => 0),
      accountsApi.list(bizId).then((a) => a.length).catch(() => 0),
    ]).then(([teamEmails, products, accounts]) => setFacts({ teamEmails, products, accounts }));
  }, [bizId, isAdmin]);

  if (!isAdmin || !facts || dismissed) return null;

  const steps: Step[] = [
    {
      key: "features", title: "Check your business type & features", href: "/settings/features",
      hint: "Batches, expiry dates, pack sizes, approvals",
      done: !!settings?.updatedBy && facts.teamEmails.includes(settings.updatedBy.toLowerCase()),
    },
    { key: "locations", title: "Add your branches", href: "/settings/locations", hint: "Shops, warehouses, storerooms", done: locations.length > 0 },
    { key: "team", title: "Add your team", href: "/settings/team", hint: "Sign-ins, roles and where each person works", done: facts.teamEmails.length > 1 },
    { key: "products", title: "Add products", href: "/products", hint: "Your catalogue, prices and units", done: facts.products > 0 },
    { key: "stock", title: "Receive opening stock", href: "/inventory", hint: "What you hold today, per location", done: hasStock },
    { key: "accounts", title: "Set up your accounts", href: "/settings/accounts", hint: "So sales and purchases reach the books", done: facts.accounts > 0 },
  ];
  const doneCount = steps.filter((s) => s.done).length;
  if (doneCount === steps.length) return null;
  const next = steps.find((s) => !s.done);

  const dismiss = () => {
    setDismissed(true);
    try { localStorage.setItem(key, "1"); } catch { /* not remembered */ }
  };

  return (
    <Panel className="relative">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-ink">Get {activeBusiness?.businessName} ready</h2>
          <p className="mt-0.5 text-[13px] text-ink-3">{doneCount} of {steps.length} done{next ? ` · next: ${next.title.toLowerCase()}` : ""}</p>
        </div>
        <button onClick={dismiss} aria-label="Hide the setup checklist" className="flex h-9 w-9 items-center justify-center rounded-xl bg-surface-2 text-ink-3 hover:text-ink">
          <X className="h-4 w-4" />
        </button>
      </div>
      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-2" aria-hidden="true">
        <div className="h-full rounded-full bg-gradient-to-r from-brand to-accent transition-[width] duration-500" style={{ width: `${(doneCount / steps.length) * 100}%` }} />
      </div>
      <ol className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
        {steps.map((s, i) => (
          <li key={s.key}>
            <Link href={s.href}
              className={cn("flex items-center gap-3 rounded-2xl border px-3.5 py-3 transition-colors",
                s.done ? "border-transparent bg-surface-2/60" : "border-line bg-surface hover:border-brand/40 hover:bg-brand-soft/40",
                next?.key === s.key && "border-brand/40 ring-2 ring-brand/15")}>
              <span className={cn("flex h-8 w-8 shrink-0 items-center justify-center rounded-xl text-sm font-semibold",
                s.done ? "bg-brand-strong text-white" : "bg-surface-2 text-ink-2")}>
                {s.done ? <Check className="h-4 w-4" /> : i + 1}
              </span>
              <span className="min-w-0 flex-1">
                <span className={cn("block truncate text-sm font-medium", s.done ? "text-ink-3 line-through decoration-ink-3/40" : "text-ink")}>{s.title}</span>
                <span className="block truncate text-xs text-ink-3">{s.hint}</span>
              </span>
              {!s.done && <ChevronRight className="h-4 w-4 shrink-0 text-ink-3" />}
            </Link>
          </li>
        ))}
      </ol>
    </Panel>
  );
}
