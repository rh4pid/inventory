"use client";
import { useCallback, useEffect, useState } from "react";
import { users } from "@/lib/api";
import type { MyInvitation } from "@/Types";

/** Invitations waiting for your answer. Re-checks when you come back to the tab. */
export function useMyInvitations() {
  const [invitations, setInvitations] = useState<MyInvitation[]>([]);
  const [loaded, setLoaded] = useState(false);

  const reload = useCallback(async () => {
    try {
      setInvitations(await users.invitations());
    } catch {
      // Not critical: the list just stays as it was.
    } finally {
      setLoaded(true);
    }
  }, []);

  useEffect(() => {
    let alive = true;
    users.invitations().then((list) => { if (alive) setInvitations(list); }).catch(() => {}).finally(() => { if (alive) setLoaded(true); });
    const onFocus = () => { void reload(); };
    window.addEventListener("focus", onFocus);
    return () => { alive = false; window.removeEventListener("focus", onFocus); };
  }, [reload]);

  return { invitations, loaded, reload };
}
