"use client";
import { useState } from "react";
import { cn } from "@/lib/utils";

const FEATURES = [
  { n: "01", name: "Point of sale", text: "Ring up sales by the piece or the carton. Take cash, mobile money, bank transfer or credit, and print the receipt." },
  { n: "02", name: "Stock & batches", text: "Every shop, warehouse, store room and van. Batch numbers, expiry dates and low-stock alerts, per location." },
  { n: "03", name: "Transfers", text: "Move stock between locations. The same batches arrive with their cost and expiry date, so nothing gets lost on the way." },
  { n: "04", name: "Purchases", text: "Receive from suppliers in cash or on credit, part-pay later, and always see exactly what you owe." },
  { n: "05", name: "The books", text: "Every sale, purchase and payment posts a balanced journal. Profit & loss and the balance sheet are always up to date." },
  { n: "06", name: "Team & roles", text: "Invite people by @username. Owners, managers, cashiers, staff and drivers each see only what their job needs." },
];

const BUILT_FOR = ["Supermarket", "Pharmacy", "Provision shop", "Hardware", "Restaurant", "Warehouse"];

/** The "feature index" slide: numbered cards (click to select), the selected one explained, and who it's for. */
export function FeatureIndex() {
  const [active, setActive] = useState(4);
  const [business, setBusiness] = useState(0);
  const f = FEATURES[active];

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)_minmax(0,0.6fr)]">
      <div className="lp-rise" style={{ ["--d" as string]: ".1s" }}>
        <p className="lp-label mb-4 flex items-center gap-2.5"><span className="lp-tag">01</span> Features</p>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {FEATURES.map((x, i) => (
            <button
              key={x.n}
              type="button"
              onClick={() => setActive(i)}
              aria-pressed={active === i}
              className={cn(
                "flex aspect-[4/3.4] flex-col justify-between border p-3 text-left transition-colors sm:aspect-square",
                active === i ? "border-brand bg-brand text-white" : "border-white/25 hover:border-white/60"
              )}
            >
              <span className="text-[11px]">{x.n}</span>
              <span className="lp-label leading-snug">{x.name}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="lp-rise" style={{ ["--d" as string]: ".25s" }}>
        <p className="lp-label mb-4 flex items-center gap-2.5"><span className="lp-tag">02</span> Selected</p>
        <div className="flex min-h-[15rem] flex-col justify-between border border-white/25 p-5" aria-live="polite">
          <div>
            <span className="inline-block bg-brand px-3 py-1.5 text-[11px] uppercase tracking-[.14em] text-white">{f.name}</span>
            <p className="mt-5 text-[13px] uppercase leading-relaxed tracking-[.06em] text-white/85">{f.text}</p>
          </div>
          <p className="lp-label text-white/50">{f.n} / 06</p>
        </div>
      </div>

      <div className="lp-rise" style={{ ["--d" as string]: ".4s" }}>
        <p className="lp-label mb-4 flex items-center gap-2.5"><span className="lp-tag">03</span> Built for</p>
        <div className="flex flex-wrap gap-2 lg:flex-col">
          {BUILT_FOR.map((b, i) => (
            <button
              key={b}
              type="button"
              onClick={() => setBusiness(i)}
              aria-pressed={business === i}
              className={cn(
                "border px-3 py-2 text-left text-[11px] uppercase tracking-[.12em] transition-colors",
                business === i ? "border-brand bg-brand text-white" : "border-white/25 hover:border-white/60"
              )}
            >
              {b}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
