"use client"; // Error boundaries must be Client Components
import { useEffect } from "react";
import Link from "next/link";
import { RefreshCw, TriangleAlert } from "lucide-react";

/** Shown inside the app frame when a page crashes, instead of a blank screen. */
export default function DashboardError({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => { console.error(error); }, [error]);

  return (
    <div className="mx-auto mt-16 max-w-md rounded-4xl border border-line/80 bg-surface p-8 text-center shadow-card">
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-accent-soft text-accent-deep">
        <TriangleAlert className="h-5 w-5" />
      </span>
      <h1 className="mt-4 text-lg font-semibold text-ink">This page ran into a problem</h1>
      <p className="mt-1 text-sm text-ink-3">
        Your data is safe. Try again, or go back to the overview.
        {error.digest && <span className="mt-2 block font-mono text-xs">Reference: {error.digest}</span>}
      </p>
      <div className="mt-5 flex justify-center gap-2">
        <button onClick={() => retry()}
          className="inline-flex h-10 items-center gap-2 rounded-xl bg-brand-strong px-4 text-sm font-medium text-white hover:bg-brand-deep">
          <RefreshCw className="h-4 w-4" /> Try again
        </button>
        <Link href="/overview" className="inline-flex h-10 items-center rounded-xl border border-line bg-surface px-4 text-sm font-medium text-ink-2 hover:bg-surface-2">
          Overview
        </Link>
      </div>
    </div>
  );
}
