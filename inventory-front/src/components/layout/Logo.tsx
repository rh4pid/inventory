import { cn } from "@/lib/utils";

/** Three stacked cubes, the product mark. */
export function LogoMark({ className }: { className?: string }) {
  const cube = (x: number, y: number) => (
    <g transform={`translate(${x} ${y})`}>
      <path d="M10 0 20 5.5 10 11 0 5.5Z" fill="#ff8a5c" />
      <path d="M0 5.5 10 11v11L0 16.5Z" fill="#f2622e" />
      <path d="M20 5.5 10 11v11l10-5.5Z" fill="#d9481a" />
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
  return <span className={cn("text-[26px] font-semibold tracking-tight text-ink", className)}>Inventory</span>;
}
