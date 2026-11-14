"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

export interface ConfirmOptions {
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** "danger" for removing or resetting things. */
  tone?: "danger" | "default";
}

type Handler = (o: ConfirmOptions) => Promise<boolean>;
let handler: Handler | null = null;

/**
 * Ask the user to confirm, in the app's own styled dialog. Resolves true for the confirm button,
 * false for cancel / closing. (Replaces the browser's plain window.confirm.)
 */
export function confirmAction(options: ConfirmOptions | string): Promise<boolean> {
  const o = typeof options === "string" ? { title: options } : options;
  if (handler) return handler(o);
  return Promise.resolve(typeof window !== "undefined" && window.confirm(o.title));
}

/** Renders the one confirmation dialog the whole app shares. */
export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [opts, setOpts] = useState<ConfirmOptions | null>(null);
  const resolver = useRef<((v: boolean) => void) | null>(null);

  const ask = useCallback<Handler>((o) => new Promise<boolean>((resolve) => {
    resolver.current?.(false);
    resolver.current = resolve;
    setOpts(o);
  }), []);

  useEffect(() => {
    handler = ask;
    return () => { if (handler === ask) handler = null; };
  }, [ask]);

  const close = (value: boolean) => {
    resolver.current?.(value);
    resolver.current = null;
    setOpts(null);
  };

  return (
    <>
      {children}
      <Dialog open={!!opts} onOpenChange={(o) => !o && close(false)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{opts?.title}</DialogTitle>
            {opts?.description && <DialogDescription className="whitespace-pre-line">{opts.description}</DialogDescription>}
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => close(false)}>{opts?.cancelLabel ?? "Cancel"}</Button>
            <Button variant={opts?.tone === "danger" ? "destructive" : "default"} onClick={() => close(true)} autoFocus>
              {opts?.confirmLabel ?? "Confirm"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
