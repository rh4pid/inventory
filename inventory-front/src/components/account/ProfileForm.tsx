"use client";
import { useState } from "react";
import { toast } from "sonner";
import { users } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { useAuth } from "@/context/AuthProvider";
import { UsernameField, cleanUsername, type UsernameStatus } from "@/components/account/UsernameField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** Your own name, phone and username. Your account is yours across every company. */
export function ProfileForm() {
  const { user, setUser } = useAuth();
  const [f, setF] = useState({
    firstName: user?.firstName ?? "",
    otherName: user?.otherName ?? "",
    lastName: user?.lastName ?? "",
    telephonePrimary: user?.telephonePrimary ?? "",
  });
  const [username, setUsername] = useState(user?.username ?? "");
  const [usernameStatus, setUsernameStatus] = useState<UsernameStatus>("idle");
  const [busy, setBusy] = useState(false);

  if (!user) return null;
  const changed = f.firstName !== (user.firstName ?? "") || f.otherName !== (user.otherName ?? "")
    || f.lastName !== (user.lastName ?? "") || f.telephonePrimary !== (user.telephonePrimary ?? "")
    || cleanUsername(username) !== (user.username ?? "");
  const valid = f.firstName.trim() && f.lastName.trim() && usernameStatus !== "bad" && usernameStatus !== "checking"
    && cleanUsername(username).length > 0;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!changed || !valid || busy) return;
    setBusy(true);
    try {
      const u = await users.updateProfile({
        ...f,
        // Keep the second phone the profile doesn't show here.
        telephoneSecondary: user.telephoneSecondary,
        username: cleanUsername(username),
      });
      setUser(u);
      setUsername(u.username ?? "");
      toast.success("Profile saved");
    } catch (err) {
      toast.error(errorMessage(err, "Could not save your profile"));
    } finally {
      setBusy(false);
    }
  };

  const field = (key: keyof typeof f, label: string, opts: { auto?: string; type?: string; optional?: boolean } = {}) => (
    <div className="space-y-1.5">
      <Label htmlFor={`pf-${key}`}>
        {label}{opts.optional && <span className="font-normal text-ink-3"> (optional)</span>}
      </Label>
      <Input id={`pf-${key}`} type={opts.type} autoComplete={opts.auto}
        value={f[key]} onChange={(e) => setF({ ...f, [key]: e.target.value })} />
    </div>
  );

  return (
    <form onSubmit={submit} className="space-y-4">
      <UsernameField id="pf-username" value={username} onChange={setUsername} current={user.username} onStatus={setUsernameStatus} />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {field("firstName", "First name", { auto: "given-name" })}
        {field("lastName", "Last name", { auto: "family-name" })}
        {field("otherName", "Other names", { optional: true, auto: "additional-name" })}
        {field("telephonePrimary", "Phone", { optional: true, auto: "tel", type: "tel" })}
      </div>
      <div className="flex justify-end">
        <Button type="submit" disabled={!changed || !valid || busy}>{busy ? "Saving…" : "Save changes"}</Button>
      </div>
    </form>
  );
}
