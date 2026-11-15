import { cn } from "@/lib/utils";
import { APP_NAME } from "@/lib/brand";

/** Three stacked cubes, the product mark. Coloured with the company theme's accent. */
export function LogoMark({ className }: { className?: string }) {
  const top = { fill: "color-mix(in srgb, var(--brand-orange) 70%, white)" };
  const left = { fill: "var(--brand-orange)" };
  const right = { fill: "var(--brand-orange-deep)" };
  const cube = (x: number, y: number) => (
    <g transform={`translate(${x} ${y})`}>
      <path d="M10 0 20 5.5 10 11 0 5.5Z" style={top} />
      <path d="M0 5.5 10 11v11L0 16.5Z" style={left} />
      <path d="M20 5.5 10 11v11l10-5.5Z" style={right} />
    </g>
  );
  return (
    <svg viewBox="0 0 40 38" className={cn("h-9 w-9", className)} aria-hidden="true">
      {cube(10, 0)}
      {cube(0, 15)}
      {cube(20, 15)}
    </svg>
  );
}

export function Wordmark({ className }: { className?: string }) {
  return <span className={cn("text-[26px] font-semibold tracking-tight text-ink", className)}>{APP_NAME}</span>;
}
