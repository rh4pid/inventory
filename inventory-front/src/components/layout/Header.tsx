"use client";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Bell, CalendarDays, ChevronDown, LayoutPanelLeft, MapPin, Menu, Search, Check, Package, CornerDownLeft,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useBusiness } from "@/context/BusinessContext";
import { useWorkspace, PERIODS } from "@/context/WorkspaceContext";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { inventory as invApi, products as productsApi, stockRequests as requestsApi } from "@/lib/api";
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent,
  DropdownMenuItem, DropdownMenuSeparator, DropdownMenuLabel,
} from "@/components/ui/dropdown-menu";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { locationTypeLabel } from "@/constants/Inventory";
import type { Product } from "@/Types";
import { Wordmark, LogoMark } from "./Logo";
import { NAV } from "./Sidebar";
import { useCan } from "@/lib/access";

const iconBtn =
  "relative flex h-11 w-11 items-center justify-center rounded-2xl border border-line bg-surface text-ink-2 " +
  "shadow-card transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand";

export function Header({ onOpenMenu }: { onOpenMenu: () => void }) {
  const { activeBusiness } = useBusiness();
  const { locations, location, setLocationId, period, setPeriod } = useWorkspace();
  const [searchOpen, setSearchOpen] = useState(false);

  // Ctrl/⌘ + K opens search.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") { e.preventDefault(); setSearchOpen(true); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <header className="flex shrink-0 flex-wrap items-center gap-3 px-4 pb-2 pt-4 md:flex-nowrap md:px-6 md:pt-6">
      {/* Brand */}
      <div className="flex min-w-0 items-center gap-3">
        <button onClick={onOpenMenu} aria-label="Open menu" className={cn(iconBtn, "md:hidden")}>
          <Menu className="h-5 w-5" />
        </button>
        <LogoMark className="h-8 w-8 md:hidden" />
        <div className="min-w-0">
          <Wordmark className="text-[22px] md:text-[28px]" />
          {activeBusiness && <p className="-mt-0.5 truncate text-xs text-ink-3">{activeBusiness.businessName}</p>}
        </div>
      </div>

      {/* Location picker (the sample's address pill) */}
      <div className="order-last w-full md:order-none md:mx-auto md:w-auto md:max-w-[28rem] md:flex-1">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              className="flex h-11 w-full items-center gap-2.5 rounded-2xl border border-line bg-surface px-4 text-left text-sm shadow-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
              aria-label="Choose location"
            >
              <MapPin className="h-4 w-4 shrink-0 text-accent" />
              <span className="min-w-0 flex-1 truncate">
                <span className="font-semibold text-ink">{location ? location.shopName : "All locations"}</span>
                <span className="text-ink-3">
                  {location
                    ? ` · ${location.shopLocation || locationTypeLabel(location.locationType)}`
                    : ` · ${locations.length} location${locations.length === 1 ? "" : "s"}`}
                </span>
              </span>
              <ChevronDown className="h-4 w-4 shrink-0 text-ink-3" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="center" className="w-[min(26rem,calc(100vw-2rem))] rounded-2xl p-1.5">
            <DropdownMenuLabel>Show figures for</DropdownMenuLabel>
            <DropdownMenuItem onClick={() => setLocationId(null)} className="rounded-xl">
              <Check className={cn("mr-2 h-4 w-4", location ? "opacity-0" : "text-accent")} />
              All my locations
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            {locations.map((s) => (
              <DropdownMenuItem key={s.id} onClick={() => setLocationId(s.id)} className="rounded-xl">
                <Check className={cn("mr-2 h-4 w-4", location?.id === s.id ? "text-accent" : "opacity-0")} />
                <div className="min-w-0">
                  <div className="font-medium text-ink">{s.shopName}</div>
                  <div className="truncate text-xs text-ink-3">
                    {locationTypeLabel(s.locationType)}{s.shopLocation ? ` · ${s.shopLocation}` : ""}
                  </div>
                </div>
              </DropdownMenuItem>
            ))}
            {locations.length === 0 && (
              <p className="px-3 py-2 text-sm text-ink-3">You are not assigned to any location yet.</p>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Period + actions */}
      <div className="ml-auto flex items-center gap-2 md:ml-0 md:gap-2.5">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className={cn(iconBtn, "w-auto gap-2 px-3.5 text-sm font-medium text-ink")} aria-label="Choose period">
              <CalendarDays className="h-4 w-4 text-ink-2" />
              <span className="hidden sm:inline">{PERIODS.find((p) => p.value === period)?.label}</span>
              <ChevronDown className="h-4 w-4 text-ink-3" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-44 rounded-2xl p-1.5">
            {PERIODS.map((p) => (
              <DropdownMenuItem key={p.value} onClick={() => setPeriod(p.value)} className="rounded-xl">
                <Check className={cn("mr-2 h-4 w-4", period === p.value ? "text-accent" : "opacity-0")} />
                {p.label} <span className="ml-auto text-xs text-ink-3">{p.days}d</span>
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>

        <button className={iconBtn} aria-label="Search (Ctrl+K)" onClick={() => setSearchOpen(true)}>
          <Search className="h-5 w-5" />
        </button>
        <Notifications />
        <BusinessSwitcher />
      </div>

      <SearchDialog open={searchOpen} onClose={() => setSearchOpen(false)} />
    </header>
  );
}

/** Stock that needs action and approvals waiting, loaded when the bell is opened (and once on load). */
function Notifications() {
  const { activeBusiness } = useBusiness();
  const { settings } = useBusinessSettings();
  const bizId = activeBusiness?.businessId;
  const isManager = useCan()("APPROVE");
  const [items, setItems] = useState<{ key: string; title: string; detail: string; href: string; tone: "orange" | "blue" }[]>([]);

  const load = useCallback(async () => {
    if (!bizId) return;
    const [low, expiring, pending] = await Promise.all([
      invApi.lowStock(bizId).catch(() => []),
      settings?.trackExpiry ? invApi.expiring(bizId).catch(() => []) : Promise.resolve([]),
      isManager ? requestsApi.pendingCount(bizId).catch(() => ({ count: 0 })) : Promise.resolve({ count: 0 }),
    ]);
    const next: typeof items = [];
    if (pending.count > 0) next.push({ key: "approvals", title: `${pending.count} approval${pending.count === 1 ? "" : "s"} waiting`, detail: "Stock-outs and adjustments from staff", href: "/inventory", tone: "blue" });
    expiring.slice(0, 5).forEach((b) => next.push({
      key: `b${b.id}`, title: b.productName,
      detail: `${b.status === "EXPIRED" ? "Expired" : "Expires"} ${b.expiryDate ?? ""} · ${b.shopName}`, href: "/inventory", tone: "orange",
    }));
    low.slice(0, 6).forEach((i) => next.push({
      key: `l${i.id}`, title: i.product.productName,
      detail: `${i.quantity <= 0 ? "Out of stock" : `Low: ${i.quantity} left`} · ${i.shop.shopName}`, href: "/inventory", tone: "orange",
    }));
    setItems(next);
  }, [bizId, settings?.trackExpiry, isManager]);

  useEffect(() => { load(); }, [load]);

  return (
    <DropdownMenu onOpenChange={(o) => o && load()}>
      <DropdownMenuTrigger asChild>
        <button className={iconBtn} aria-label={`Notifications${items.length ? ` (${items.length})` : ""}`}>
          <Bell className="h-5 w-5" />
          {items.length > 0 && (
            <span className="absolute right-2 top-2 h-2.5 w-2.5 rounded-full bg-accent ring-2 ring-surface" />
          )}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80 rounded-2xl p-1.5">
        <DropdownMenuLabel>Needs attention</DropdownMenuLabel>
        {items.length === 0 && <p className="px-3 py-3 text-sm text-ink-3">All clear. Nothing needs action.</p>}
        {items.map((n) => (
          <DropdownMenuItem key={n.key} asChild className="rounded-xl">
            <Link href={n.href} className="flex items-start gap-2.5">
              <span className={cn("mt-1.5 h-2 w-2 shrink-0 rounded-full", n.tone === "orange" ? "bg-accent" : "bg-brand")} />
              <span className="min-w-0">
                <span className="block truncate font-medium text-ink">{n.title}</span>
                <span className="block truncate text-xs text-ink-3">{n.detail}</span>
              </span>
            </Link>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function BusinessSwitcher() {
  const { businesses, activeBusiness, switchBusiness } = useBusiness();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className={iconBtn} aria-label="Switch business">
          <LayoutPanelLeft className="h-5 w-5" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64 rounded-2xl p-1.5">
        <DropdownMenuLabel>Business</DropdownMenuLabel>
        {businesses.map((b) => (
          <DropdownMenuItem key={b.businessId} onClick={() => switchBusiness(b)} className="rounded-xl">
            <Check className={cn("mr-2 h-4 w-4", b.businessId === activeBusiness?.businessId ? "text-accent" : "opacity-0")} />
            <div className="min-w-0">
              <div className="truncate font-medium text-ink">{b.businessName}</div>
              <div className="text-xs text-ink-3">{b.role}</div>
            </div>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** Jump to a page or a product. */
function SearchDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const can = useCan();
  const { activeBusiness } = useBusiness();
  const [q, setQ] = useState("");
  const [products, setProducts] = useState<Product[] | null>(null);
  const [cursor, setCursor] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    setQ(""); setCursor(0);
    if (activeBusiness && products === null) {
      productsApi.list(activeBusiness.businessId, { size: 200 }).then((p) => setProducts(p.content)).catch(() => setProducts([]));
    }
    setTimeout(() => inputRef.current?.focus(), 30);
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { setProducts(null); }, [activeBusiness?.businessId]);

  const results = useMemo(() => {
    const term = q.trim().toLowerCase();
    const pages = [...NAV.filter((n) => can(n.need)), { label: "Settings", href: "/settings" }]
      .filter((n) => !term || n.label.toLowerCase().includes(term))
      .map((n) => ({ key: n.href, label: n.label, hint: "Page", href: n.href }));
    const prods = term
      ? (products ?? []).filter((p) => p.productName.toLowerCase().includes(term) || (p.sku ?? "").toLowerCase().includes(term))
          .slice(0, 8)
          .map((p) => ({ key: `p${p.id}`, label: p.productName, hint: p.sku ? `Product · ${p.sku}` : "Product", href: `/products?q=${encodeURIComponent(p.productName)}` }))
      : [];
    return [...prods, ...pages].slice(0, 12);
  }, [q, products, can]);

  const go = (href: string) => { onClose(); router.push(href); };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-lg gap-0 p-0">
        <DialogTitle className="sr-only">Search</DialogTitle>
        <div className="flex items-center gap-3 border-b border-line px-4 py-3">
          <Search className="h-5 w-5 text-ink-3" />
          <input
            ref={inputRef}
            value={q}
            onChange={(e) => { setQ(e.target.value); setCursor(0); }}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") { e.preventDefault(); setCursor((c) => Math.min(c + 1, results.length - 1)); }
              if (e.key === "ArrowUp") { e.preventDefault(); setCursor((c) => Math.max(c - 1, 0)); }
              if (e.key === "Enter" && results[cursor]) go(results[cursor].href);
            }}
            placeholder="Search products or pages…"
            aria-label="Search products or pages"
            className="h-9 flex-1 bg-transparent text-base text-ink outline-none placeholder:text-ink-3"
          />
        </div>
        <ul className="max-h-80 overflow-y-auto p-1.5" role="listbox">
          {results.map((r, i) => (
            <li key={r.key} role="option" aria-selected={i === cursor}>
              <button
                onMouseEnter={() => setCursor(i)}
                onClick={() => go(r.href)}
                className={cn("flex w-full items-center gap-3 rounded-xl px-3 py-2 text-left text-sm", i === cursor ? "bg-surface-2" : "")}
              >
                <Package className="h-4 w-4 text-ink-3" />
                <span className="min-w-0 flex-1 truncate font-medium text-ink">{r.label}</span>
                <span className="text-xs text-ink-3">{r.hint}</span>
                {i === cursor && <CornerDownLeft className="h-3.5 w-3.5 text-ink-3" />}
              </button>
            </li>
          ))}
          {results.length === 0 && <li className="px-3 py-6 text-center text-sm text-ink-3">No matches</li>}
        </ul>
      </DialogContent>
    </Dialog>
  );
}
