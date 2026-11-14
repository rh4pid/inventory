"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Check, Plus } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { ROLE_LABEL } from "@/lib/access";
import { Monogram } from "@/components/dashboard/Panel";
import { Button } from "@/components/ui/button";
import { CreateOwnCompanyDialog } from "@/components/company/CreateOwnCompanyDialog";

/** Every company you belong to, your role in each, and switching between them. */
export function MyCompanies() {
  const router = useRouter();
  const { businesses, activeBusiness, switchBusiness } = useBusiness();
  const [creating, setCreating] = useState(false);

  return (
    <section className="rounded-4xl border border-line/80 bg-surface p-6 shadow-card">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-ink">My companies</h2>
          <p className="text-sm text-ink-3">Where you work and what you can do in each. Switch any time.</p>
        </div>
        <Button size="sm" onClick={() => setCreating(true)}><Plus className="mr-1 h-4 w-4" /> Create company</Button>
      </div>

      {businesses.length === 0 ? (
        <p className="rounded-2xl bg-surface-2 px-4 py-6 text-center text-sm text-ink-3">You&apos;re not in any company yet.</p>
      ) : (
        <ul className="divide-y divide-line/70">
          {businesses.map((b) => {
            const current = b.businessId === activeBusiness?.businessId;
            return (
              <li key={b.businessId} className="flex items-center gap-3 py-3">
                <Monogram name={b.businessName} className="h-10 w-10 shrink-0" />
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium text-ink">{b.businessName}</div>
                  <div className="text-xs text-ink-3">{ROLE_LABEL[b.role] ?? b.role} · {b.businessCode}</div>
                </div>
                {current ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-brand-soft px-2.5 py-1 text-xs font-medium text-brand-deep">
                    <Check className="h-3.5 w-3.5" /> Current
                  </span>
                ) : (
                  <Button size="sm" variant="outline" onClick={() => { switchBusiness(b); router.push("/overview"); }}>
                    Switch
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <CreateOwnCompanyDialog open={creating} onClose={() => setCreating(false)} />
    </section>
  );
}
