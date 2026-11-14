"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Mail } from "lucide-react";
import { toast } from "sonner";
import { users } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { ROLE_LABEL } from "@/lib/access";
import { useBusiness } from "@/context/BusinessContext";
import { useMyInvitations } from "@/hooks/useMyInvitations";
import { Monogram } from "@/components/dashboard/Panel";
import { Button } from "@/components/ui/button";
import type { MyInvitation } from "@/Types";

const daysLeft = (iso: string) => Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000));

/** Invitations waiting for you: accept to join the company, or decline. Shows nothing when there are none. */
export function PendingInvites() {
  const router = useRouter();
  const { refreshBusinesses } = useBusiness();
  const { invitations, reload } = useMyInvitations();
  const [busyId, setBusyId] = useState<number | null>(null);

  if (invitations.length === 0) return null;

  const accept = async (inv: MyInvitation) => {
    setBusyId(inv.id);
    try {
      const m = await users.acceptInvitation(inv.id);
      await refreshBusinesses(m.businessId);
      toast.success(`You joined ${m.businessName} as ${ROLE_LABEL[m.role] ?? m.role}`);
      await reload();
      router.push("/overview");
    } catch (err) {
      toast.error(errorMessage(err, "Could not accept the invitation"));
      await reload();
    } finally {
      setBusyId(null);
    }
  };

  const decline = async (inv: MyInvitation) => {
    setBusyId(inv.id);
    try {
      await users.declineInvitation(inv.id);
      toast.success(`Invitation from ${inv.businessName} declined`);
    } catch (err) {
      toast.error(errorMessage(err, "Could not decline the invitation"));
    } finally {
      setBusyId(null);
      await reload();
    }
  };

  return (
    <section className="rounded-4xl border border-brand/30 bg-surface p-6 shadow-card" aria-labelledby="invites-title">
      <h2 id="invites-title" className="flex items-center gap-2 text-lg font-semibold text-ink">
        <Mail className="h-5 w-5 text-brand-deep" /> Invitations
      </h2>
      <ul className="mt-3 divide-y divide-line/70">
        {invitations.map((inv) => (
          <li key={inv.id} className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center">
            <div className="flex min-w-0 flex-1 items-center gap-3">
              <Monogram name={inv.businessName} className="h-10 w-10 shrink-0" />
              <div className="min-w-0">
                <div className="truncate font-medium text-ink">{inv.businessName}</div>
                <div className="text-xs text-ink-3">
                  {inv.invitedBy} invited you as <b className="font-medium text-ink-2">{ROLE_LABEL[inv.role] ?? inv.role}</b>
                  {inv.locations.length > 0 && <> at {inv.locations.join(", ")}</>}
                  {" · "}{daysLeft(inv.expiresAt)} days left
                </div>
              </div>
            </div>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" disabled={busyId === inv.id} onClick={() => decline(inv)}>Decline</Button>
              <Button size="sm" disabled={busyId === inv.id} onClick={() => accept(inv)}>Accept</Button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
