import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { cn } from "@/lib/utils";

/** A dashboard card: big radius, generous padding; `tone` gives the saturated brand cards. */
export function Panel({ tone = "plain", className, children, ...rest }: {
  tone?: "plain" | "blue" | "soft";
} & React.HTMLAttributes<HTMLElement>) {
  return (
    <section
      className={cn(
        "relative overflow-hidden rounded-4xl p-5 md:p-6",
        tone === "plain" && "border border-line/80 bg-surface shadow-card",
        tone === "blue" && "bg-brand-strong text-white shadow-[var(--shadow-brand-lg)]",
        tone === "soft" && "border border-brand/15 bg-brand-soft text-ink shadow-card",
        className
      )}
      {...rest}
    >
      {children}
    </section>
  );
}

export function PanelHeader({ title, subtitle, href, linkLabel, tone = "plain", children }: {
  title: string;
  subtitle?: React.ReactNode;
  href?: string;
  linkLabel?: string;
  tone?: "plain" | "blue" | "soft";
  children?: React.ReactNode;
}) {
  return (
    <div className="relative z-10 flex items-start justify-between gap-3">
      <div className="min-w-0">
        <h2 className={cn("text-xl font-semibold tracking-tight", tone === "blue" ? "text-white" : "text-ink")}>
          {title}
        </h2>
        {subtitle && (
          <p className={cn("mt-0.5 text-[13px]", tone === "blue" ? "text-white/80" : tone === "soft" ? "text-ink-2" : "text-ink-3")}>
            {subtitle}
          </p>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        {children}
        {href && (
          <Link
            href={href}
            aria-label={linkLabel ?? `Open ${title}`}
            className={cn(
              "flex h-9 w-9 items-center justify-center rounded-xl transition-colors focus-visible:outline-none focus-visible:ring-2",
              tone === "blue" && "bg-white/15 text-white hover:bg-white/25 focus-visible:ring-white",
              tone === "soft" && "bg-white/80 text-ink-2 hover:bg-white hover:text-ink focus-visible:ring-brand",
              tone === "plain" && "bg-surface-2 text-ink-2 hover:text-ink focus-visible:ring-brand"
            )}
          >
            <ArrowUpRight className="h-4 w-4" />
          </Link>
        )}
      </div>
    </div>
  );
}

const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });
export const fmtNum = (n: number) => nf.format(n);
export const fmtCompact = (n: number) =>
  Math.abs(n) >= 10_000 ? new Intl.NumberFormat(undefined, { notation: "compact", maximumFractionDigits: 1 }).format(n) : nf.format(n);

/** Initials tile for a product or person; colour is stable per name. */
export function Monogram({ name, className }: { name: string; className?: string }) {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  const tones = ["bg-brand-soft text-brand-deep", "bg-accent-soft text-accent-deep", "bg-surface-2 text-ink-2"];
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join("").toUpperCase() || "?";
  return (
    <span className={cn("flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl text-sm font-semibold", tones[h % tones.length], className)} aria-hidden="true">
      {initials}
    </span>
  );
}
