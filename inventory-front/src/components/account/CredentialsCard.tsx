"use client";
import { useState } from "react";
import { Check, Copy, KeyRound, ShieldAlert } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * A sign-in to hand over to someone (there is no email sending). The temporary password is
 * shown only here, once; the person must choose their own password when they first sign in.
 */
export function CredentialsCard({ name, email, temporaryPassword, existingAccount, title }: {
  name: string;
  email: string;
  temporaryPassword: string | null;
  existingAccount?: boolean;
  title?: string;
}) {
  const [copied, setCopied] = useState(false);
  const origin = typeof window !== "undefined" ? window.location.origin : "";

  if (!temporaryPassword) {
    return (
      <div className="rounded-2xl border border-line bg-surface-2 p-4 text-sm text-ink-2">
        <p className="font-semibold text-ink">{name} already has a sign-in</p>
        <p className="mt-1">
          {email} {existingAccount ? "already had an account (for example with another company)" : "keeps their password"}.
          They sign in with their usual password and will now see this company.
        </p>
      </div>
    );
  }

  const text =
    `Hello ${name.split(" ")[0]},\n\nYour Inventory sign-in:\n` +
    `Sign in at: ${origin}/login\nEmail: ${email}\nTemporary password: ${temporaryPassword}\n\n` +
    `You'll be asked to choose your own password when you first sign in.`;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* clipboard blocked: the details are on screen to copy by hand */ }
  };

  return (
    <div className="space-y-3">
      <div className="rounded-2xl border border-brand/20 bg-brand-soft p-4">
        <p className="flex items-center gap-2 text-sm font-semibold text-brand-deep">
          <KeyRound className="h-4 w-4" /> {title ?? "Sign-in to hand over"}
        </p>
        <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
          <dt className="text-ink-3">Name</dt><dd className="font-medium text-ink">{name}</dd>
          <dt className="text-ink-3">Email</dt><dd className="break-all font-medium text-ink">{email}</dd>
          <dt className="text-ink-3">Temporary password</dt>
          <dd><code className="rounded-lg bg-surface px-2 py-0.5 font-mono text-[15px] font-semibold tracking-wide text-ink select-all">{temporaryPassword}</code></dd>
        </dl>
      </div>
      <p className="flex items-start gap-2 text-xs text-ink-3">
        <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent" />
        This password is shown only now. Send it privately; they&apos;ll choose their own at first sign-in.
      </p>
      <Button type="button" variant="outline" className="w-full" onClick={copy}>
        {copied ? <Check className="h-4 w-4 text-brand" /> : <Copy className="h-4 w-4" />}
        {copied ? "Copied" : "Copy sign-in message"}
      </Button>
    </div>
  );
}
