"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Briefcase, Building2, CalendarDays, Clock, Mail, MapPin, Phone, Users, Wallet } from "lucide-react";
import { toast } from "sonner";
import { users, businessLogoUrl } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { ROLE_LABEL } from "@/lib/access";
import { useBusiness } from "@/context/BusinessContext";
import { useMyInvitations } from "@/hooks/useMyInvitations";
import { Monogram } from "@/components/dashboard/Panel";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import type { MyInvitation } from "@/Types";

const daysLeft = (iso: string) => Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000));
const longDate = (iso?: string | null) =>
  iso ? new Date(iso.length === 10 ? `${iso}T00:00:00` : iso).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" }) : null;
const title = (s?: string | null) => (s ? s.charAt(0) + s.slice(1).toLowerCase() : "");

function CompanyBadge({ inv, className }: { inv: MyInvitation; className?: string }) {
  const src = businessLogoUrl(inv.businessLogo);
  // eslint-disable-next-line @next/next/no-img-element
  if (src) return <img src={src} alt="" className={`shrink-0 rounded-2xl border border-line/70 bg-white object-contain ${className ?? "h-10 w-10"}`} />;
  return <Monogram name={inv.businessName} className={`shrink-0 ${className ?? "h-10 w-10"}`} />;
}

function Row({ icon: Icon, label, children }: { icon: React.ComponentType<{ className?: string }>; label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-3">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-ink-3" />
      <div className="min-w-0">
        <div className="text-xs font-medium uppercase tracking-[0.1em] text-ink-3">{label}</div>
        <div className="text-sm text-ink">{children}</div>
      </div>
    </div>
  );
}

/** Everything about the offer and the company, so the person can decide before accepting. */
function InvitationDetails({ inv, busy, onClose, onAccept, onDecline }: {
  inv: MyInvitation; busy: boolean; onClose: () => void; onAccept: () => void; onDecline: () => void;
}) {
  const o = inv.offer ?? {};
  const c = inv.company;
  const role = ROLE_LABEL[inv.role] ?? inv.role;
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90dvh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center gap-3">
            <CompanyBadge inv={inv} className="h-12 w-12" />
            <div className="min-w-0 text-left">
              <DialogTitle className="truncate">{inv.businessName}</DialogTitle>
              <p className="text-sm text-ink-3">
                Invited by {inv.invitedBy}{inv.invitedByUsername ? ` (@${inv.invitedByUsername})` : ""} · {daysLeft(inv.expiresAt)} days left to answer
              </p>
            </div>
          </div>
        </DialogHeader>

        <section className="space-y-3 rounded-2xl bg-surface-2 p-4">
          <h3 className="text-sm font-semibold text-ink">The position</h3>
          <Row icon={Briefcase} label="Position">
            {o.jobTitle ? <>{o.jobTitle} <span className="text-ink-3">· {role} access</span></> : role}
          </Row>
          {o.workingHours && <Row icon={Clock} label="Working hours"><span className="whitespace-pre-line">{o.workingHours}</span></Row>}
          {o.startDate && <Row icon={CalendarDays} label="Start date">{longDate(o.startDate)}</Row>}
          {o.pay && <Row icon={Wallet} label="Pay">{o.pay}</Row>}
          <Row icon={MapPin} label={inv.locations.length > 1 ? "Locations" : "Location"}>
            {inv.locations.length === 0 ? "All locations" : (
              <ul className="space-y-0.5">
                {inv.locations.map((l) => (
                  <li key={l.name}>
                    {l.name}
                    {(l.type || l.address) && <span className="text-ink-3"> · {[title(l.type), l.address].filter(Boolean).join(", ")}</span>}
                  </li>
                ))}
              </ul>
            )}
          </Row>
          {o.message && (
            <blockquote className="border-l-2 border-brand/50 pl-3 text-sm italic text-ink-2 whitespace-pre-line">{o.message}</blockquote>
          )}
        </section>

        {c && (
          <section className="space-y-3 rounded-2xl border border-line/80 p-4">
            <h3 className="text-sm font-semibold text-ink">About the company</h3>
            {c.businessType && <Row icon={Building2} label="Business">{c.businessType}</Row>}
            <Row icon={Users} label="Team">
              {c.teamSize} {c.teamSize === 1 ? "person" : "people"} · {c.locationCount} {c.locationCount === 1 ? "location" : "locations"}
              {c.since && <span className="text-ink-3"> · on Inventory since {new Date(c.since).getFullYear()}</span>}
            </Row>
            {c.email && <Row icon={Mail} label="Email"><a className="text-brand-deep hover:underline" href={`mailto:${c.email}`}>{c.email}</a></Row>}
            {(c.phone || c.otherPhone) && (
              <Row icon={Phone} label="Phone">{[c.phone, c.otherPhone].filter(Boolean).join(" · ")}</Row>
            )}
          </section>
        )}

        <DialogFooter>
          <Button variant="outline" disabled={busy} onClick={onDecline}>Decline</Button>
          <Button disabled={busy} onClick={onAccept}>Accept and join</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Invitations waiting for you: see the details, accept to join the company, or decline. Shows nothing when there are none. */
export function PendingInvites() {
  const router = useRouter();
  const { refreshBusinesses } = useBusiness();
  const { invitations, reload } = useMyInvitations();
  const [busyId, setBusyId] = useState<number | null>(null);
  const [openId, setOpenId] = useState<number | null>(null);

  if (invitations.length === 0) return null;
  const open = invitations.find((i) => i.id === openId) ?? null;

  const accept = async (inv: MyInvitation) => {
    setBusyId(inv.id);
    try {
      const m = await users.acceptInvitation(inv.id);
      setOpenId(null);
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
      setOpenId(null);
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
            <button type="button" onClick={() => setOpenId(inv.id)}
              className="flex min-w-0 flex-1 items-center gap-3 rounded-2xl text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand">
              <CompanyBadge inv={inv} />
              <div className="min-w-0">
                <div className="truncate font-medium text-ink">{inv.businessName}</div>
                <div className="text-xs text-ink-3">
                  {inv.invitedBy} invited you as <b className="font-medium text-ink-2">{inv.offer?.jobTitle || (ROLE_LABEL[inv.role] ?? inv.role)}</b>
                  {inv.locations.length > 0 && <> at {inv.locations.map((l) => l.name).join(", ")}</>}
                  {" · "}{daysLeft(inv.expiresAt)} days left
                </div>
                <div className="text-xs font-medium text-brand-deep">View details</div>
              </div>
            </button>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" disabled={busyId === inv.id} onClick={() => decline(inv)}>Decline</Button>
              <Button size="sm" disabled={busyId === inv.id} onClick={() => accept(inv)}>Accept</Button>
            </div>
          </li>
        ))}
      </ul>
      {open && (
        <InvitationDetails inv={open} busy={busyId === open.id} onClose={() => setOpenId(null)}
          onAccept={() => accept(open)} onDecline={() => decline(open)} />
      )}
    </section>
  );
}
