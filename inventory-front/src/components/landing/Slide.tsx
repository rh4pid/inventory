"use client";
import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

/**
 * One "slide" of the landing page. When a third of it scrolls into view it gets .is-in, which
 * starts its reveal transitions and line drawings (see .lp-* in globals.css). Plays once.
 */
export function Slide({ tone, id, className, children }: {
  tone: "dark" | "light" | "blue";
  id?: string;
  className?: string;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLElement>(null);
  const [inView, setInView] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(([e]) => {
      if (e.isIntersecting) { setInView(true); io.disconnect(); }
    }, { threshold: 0.28 });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <section
      ref={ref}
      id={id}
      className={cn(
        "lp-slide relative overflow-hidden",
        tone === "dark" && "lp-dark",
        tone === "light" && "lp-light",
        tone === "blue" && "bp-sheet-sm bp-sheet-lg bg-brand-deep text-white",
        inView && "is-in",
        className
      )}
    >
      {children}
    </section>
  );
}

/** A number that counts up from 0 when it first scrolls into view. */
export function CountUp({ to, pad = 2 }: { to: number; pad?: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  const [n, setN] = useState(to);

  useEffect(() => {
    const el = ref.current;
    if (!el || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let raf = 0;
    const io = new IntersectionObserver(([e]) => {
      if (!e.isIntersecting) return;
      io.disconnect();
      const t0 = performance.now();
      const step = (now: number) => {
        const p = Math.min(1, (now - t0) / 1200);
        setN(Math.round(to * (1 - Math.pow(1 - p, 3))));
        if (p < 1) raf = requestAnimationFrame(step);
      };
      raf = requestAnimationFrame(step);
    }, { threshold: 0.6 });
    // Start from zero only once we know it will animate (no flash of 0 without JS), then watch.
    const first = requestAnimationFrame(() => { setN(0); io.observe(el); });
    return () => { io.disconnect(); cancelAnimationFrame(raf); cancelAnimationFrame(first); };
  }, [to]);

  return <span ref={ref} className="tabular-nums">{String(n).padStart(pad, "0")}</span>;
}
