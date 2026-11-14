"use client";
import { useAuth } from "@/context/AuthProvider";
import { useBusiness } from "@/context/BusinessContext";
import { PageHeader } from "@/components/shared/PageHeader";
import { ChangePasswordForm } from "@/components/account/ChangePasswordForm";
import { ProfileForm } from "@/components/account/ProfileForm";
import { Monogram } from "@/components/dashboard/Panel";

export default function ProfilePage() {
  const { user } = useAuth();
  const { activeBusiness } = useBusiness();
  const name = `${user?.firstName ?? ""} ${user?.lastName ?? ""}`.trim();

  return (
    <div className="max-w-3xl">
      <PageHeader title="Profile" description="Your account, username and password" />
      <div className="grid gap-5 md:grid-cols-2">
        <section className="rounded-4xl border border-line/80 bg-surface p-6 shadow-card">
          <div className="flex items-center gap-3">
            <Monogram name={name || "?"} className="h-14 w-14 text-base" />
            <div className="min-w-0">
              <div className="truncate text-lg font-semibold text-ink">{name}</div>
              {user?.username && <div className="truncate text-sm font-medium text-brand-deep">@{user.username}</div>}
              <div className="truncate text-sm text-ink-3">{user?.email}</div>
            </div>
          </div>
          {activeBusiness && (
            <dl className="mt-5 grid grid-cols-2 gap-3 text-sm">
              <div className="rounded-2xl bg-surface-2 px-3 py-2.5">
                <dt className="text-xs text-ink-3">Company</dt>
                <dd className="truncate font-medium text-ink">{activeBusiness.businessName}</dd>
              </div>
              <div className="rounded-2xl bg-surface-2 px-3 py-2.5">
                <dt className="text-xs text-ink-3">Role</dt>
                <dd className="font-medium capitalize text-ink">{activeBusiness.role.toLowerCase()}</dd>
              </div>
            </dl>
          )}
        </section>
        <section className="rounded-4xl border border-line/80 bg-surface p-6 shadow-card md:row-span-2">
          <h2 className="mb-4 text-lg font-semibold text-ink">Your details</h2>
          <ProfileForm key={user?.id} />
          <p className="mt-4 text-xs text-ink-3">Your account is yours in every company you work with. Your email can&apos;t be changed here.</p>
        </section>
        <section className="rounded-4xl border border-line/80 bg-surface p-6 shadow-card">
          <h2 className="mb-4 text-lg font-semibold text-ink">Change password</h2>
          <ChangePasswordForm />
        </section>
      </div>
    </div>
  );
}
