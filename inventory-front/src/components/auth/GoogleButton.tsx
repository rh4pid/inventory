"use client";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { auth } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { useAuth } from "@/context/AuthProvider";

/* Google Identity Services (https://accounts.google.com/gsi/client), only the parts used here. */
interface GoogleId {
  initialize: (o: { client_id: string; callback: (r: { credential?: string }) => void; ux_mode?: "popup"; auto_select?: boolean }) => void;
  renderButton: (el: HTMLElement, o: Record<string, unknown>) => void;
}
declare global {
  interface Window { google?: { accounts: { id: GoogleId } } }
}

const SCRIPT = "https://accounts.google.com/gsi/client";
let scriptPromise: Promise<void> | null = null;
function loadScript() {
  if (window.google?.accounts?.id) return Promise.resolve();
  scriptPromise ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement("script");
    s.src = SCRIPT;
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => { scriptPromise = null; reject(new Error("Couldn't load Google sign-in")); };
    document.head.appendChild(s);
  });
  return scriptPromise;
}

let clientIdPromise: Promise<string | null> | null = null;
const googleClientId = () => (clientIdPromise ??= auth.options().then((o) => o.googleClientId).catch(() => { clientIdPromise = null; return null; }));

/**
 * "Continue with Google". Shows nothing until the backend says Google sign-in is switched on
 * (GOOGLE_CLIENT_ID), so the page looks the same without it. A first-time Google user gets an account.
 */
export function GoogleButton({ text = "continue_with", withDivider = true }: { text?: "continue_with" | "signup_with" | "signin_with"; withDivider?: boolean }) {
  const { signInWithGoogle } = useAuth();
  const router = useRouter();
  const box = useRef<HTMLDivElement | null>(null);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const clientId = await googleClientId();
      if (!clientId || cancelled) return;
      try { await loadScript(); } catch { return; }
      const g = window.google?.accounts?.id;
      if (!g || cancelled || !box.current) return;
      g.initialize({
        client_id: clientId,
        ux_mode: "popup",
        callback: async ({ credential }) => {
          if (!credential) return;
          setBusy(true);
          try {
            const u = await signInWithGoogle(credential);
            router.push(u.superAdmin ? "/admin" : "/overview");
          } catch (err) {
            toast.error(errorMessage(err, "Google sign-in failed"));
            setBusy(false);
          }
        },
      });
      g.renderButton(box.current, {
        type: "standard", theme: "outline", size: "large", shape: "pill", text,
        width: Math.min(400, box.current.offsetWidth || 320),
      });
      setReady(true);
    })();
    return () => { cancelled = true; };
  }, [signInWithGoogle, router, text]);

  return (
    <div className={ready ? "space-y-4" : "hidden"} aria-busy={busy}>
      {withDivider && (
        <div className="flex items-center gap-3 text-xs uppercase tracking-[0.14em] text-ink-3">
          <span className="h-px flex-1 bg-line" /> or <span className="h-px flex-1 bg-line" />
        </div>
      )}
      <div ref={box} className={busy ? "pointer-events-none flex w-full justify-center opacity-60" : "flex w-full justify-center"} />
    </div>
  );
}
