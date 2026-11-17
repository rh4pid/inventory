"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
  Home, ScanLine, Package, Tag, Warehouse, ShoppingBag, Truck, Users, Building2,
  Wallet, FileSpreadsheet, SlidersHorizontal, Settings, LogOut, X, Receipt, Landmark,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/context/AuthProvider";
import { useBusiness } from "@/context/BusinessContext";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent,
  DropdownMenuItem, DropdownMenuSeparator, DropdownMenuLabel,
} from "@/components/ui/dropdown-menu";
import { CompanyLogo } from "./CompanyLogo";
import { useCan, UPLOAD_CAPS, type Capability } from "@/lib/access";

type Icon = React.ComponentType<{ className?: string }>;

/** Main menu; each item shows only for roles that can use it (see lib/access.ts). */
export const NAV: { label: string; href: string; icon: Icon; need?: Capability | Capability[] }[] = [
  { label: "Overview",   href: "/overview",   icon: Home },
  { label: "Point of sale", href: "/pos",     icon: ScanLine, need: "SELL" },
  { label: "Products",   href: "/products",   icon: Package, need: "VIEW_STOCK" },
  { label: "Categories", href: "/categories", icon: Tag, need: "MANAGE_CATALOG" },
  { label: "Features",   href: "/features",   icon: SlidersHorizontal, need: "MANAGE_CATALOG" },
  { label: "Inventory",  href: "/inventory",  icon: Warehouse, need: "VIEW_STOCK" },
  { label: "Sales",      href: "/sales",      icon: ShoppingBag, need: "SELL" },
  { label: "Purchases",  href: "/purchases",  icon: Truck, need: "RECEIVE" },
  { label: "Customers",  href: "/customers",  icon: Users, need: "MANAGE_CUSTOMERS" },
  { label: "Suppliers",  href: "/suppliers",  icon: Building2, need: "MANAGE_SUPPLIERS" },
  { label: "Expenses",   href: "/expenses",   icon: Receipt, need: ["RECORD_EXPENSES", "APPROVE"] },
  { label: "Accounts",   href: "/accounting", icon: Landmark, need: "MANAGE_ACCOUNTS" },
  { label: "Payroll",    href: "/payroll",    icon: Wallet, need: "MANAGE_PAYROLL" },
  { label: "Templates",  href: "/templates",  icon: FileSpreadsheet, need: UPLOAD_CAPS },
];

const isActive = (pathname: string, href: string) => pathname === href || pathname.startsWith(href + "/");

/** Rail buttons shrink on short screens (laptops, zoomed-in browsers) so the whole menu fits. */
const SHORT = "[@media(max-height:860px)]:h-9 [@media(max-height:860px)]:w-9 [@media(max-height:860px)]:rounded-xl";

function RailLink({ href, label, icon: Icon, active }: { href: string; label: string; icon: Icon; active: boolean }) {
  // The tooltip is placed on the screen (position: fixed) so the scrolling menu can't clip it.
  const [tip, setTip] = useState<{ x: number; y: number } | null>(null);
  const showTip = (e: React.SyntheticEvent<HTMLElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    setTip({ x: r.right + 12, y: r.top + r.height / 2 });
  };
  return (
    <Link
      href={href}
      aria-label={label}
      aria-current={active ? "page" : undefined}
      onMouseEnter={showTip}
      onFocus={showTip}
      onMouseLeave={() => setTip(null)}
      onBlur={() => setTip(null)}
      className={cn(
        "relative flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl transition-all",
        SHORT,
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2 focus-visible:ring-offset-frame",
        active
          ? "bg-brand-strong text-white shadow-[var(--shadow-brand)]"
          : "bg-surface-2 text-ink-2 hover:bg-white hover:text-ink hover:shadow-card"
      )}
    >
      <Icon className="h-[20px] w-[20px] [@media(max-height:860px)]:h-[18px] [@media(max-height:860px)]:w-[18px]" />
      {tip && (
        <span
          role="tooltip"
          style={{ left: tip.x, top: tip.y }}
          className="pointer-events-none fixed z-50 -translate-y-1/2 whitespace-nowrap rounded-lg bg-ink px-2.5 py-1 text-xs font-medium text-white shadow-float"
        >
          {label}
        </span>
      )}
    </Link>
  );
}

function UserMenu({ align = "start" }: { align?: "start" | "end" }) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const initials = user ? `${user.firstName?.[0] ?? ""}${user.lastName?.[0] ?? ""}`.toUpperCase() : "?";

  const signOut = async () => {
    await logout();
    toast.success("Signed out");
    router.push("/login");
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          aria-label="Your account"
          className="rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2"
        >
          <Avatar className={cn("h-11 w-11 rounded-2xl", SHORT)}>
            {user?.profilePicture && <AvatarImage src={user.profilePicture} className="rounded-2xl object-cover" />}
            <AvatarFallback className="rounded-2xl bg-brand-strong text-sm font-semibold text-white">
              {initials}
            </AvatarFallback>
          </Avatar>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent side="right" align={align} className="w-56">
        <DropdownMenuLabel>
          <div className="flex flex-col">
            <span className="font-medium text-ink">{user?.firstName} {user?.lastName}</span>
            <span className="text-xs font-normal text-ink-3">{user?.email}</span>
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={signOut} className="text-red-600 focus:bg-red-50 focus:text-red-600">
          <LogOut className="mr-2 h-4 w-4" /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** The icon rail on the left of the app frame (desktop). */
export function Sidebar() {
  const pathname = usePathname();
  const can = useCan();
  return (
    // Full height, never taller than the screen: the menu scrolls if it must, Settings and your account stay visible.
    <aside className="hidden h-full min-h-0 w-[88px] shrink-0 flex-col items-center py-5 md:flex [@media(max-height:860px)]:py-3">
      <Link href="/overview" aria-label="Home" className="mb-4 flex h-12 w-12 shrink-0 items-center justify-center [@media(max-height:860px)]:mb-2 [@media(max-height:860px)]:h-10 [@media(max-height:860px)]:w-10">
        <CompanyLogo className="[@media(max-height:860px)]:h-8 [@media(max-height:860px)]:w-8" />
      </Link>
      <nav aria-label="Main" className="no-scrollbar flex min-h-0 flex-1 flex-col items-center gap-2 overflow-y-auto overflow-x-hidden px-2 py-1 [@media(max-height:860px)]:gap-1.5">
        {NAV.filter((item) => can(item.need)).map((item) => (
          <RailLink key={item.href} {...item} active={isActive(pathname, item.href)} />
        ))}
      </nav>
      <div className="mt-3 flex shrink-0 flex-col items-center gap-3 border-t border-line/70 pt-3 [@media(max-height:860px)]:mt-2 [@media(max-height:860px)]:gap-2 [@media(max-height:860px)]:pt-2">
        <RailLink href="/settings" label="Settings" icon={Settings} active={isActive(pathname, "/settings")} />
        <UserMenu align="end" />
      </div>
    </aside>
  );
}

/** Full-screen navigation for small screens, opened from the header's menu button. */
export function MobileNav({ open, onClose }: { open: boolean; onClose: () => void }) {
  const pathname = usePathname();
  const can = useCan();
  const { activeBusiness } = useBusiness();

  useEffect(() => { onClose(); }, [pathname]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 md:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
      <div className="absolute inset-0 bg-ink/30 backdrop-blur-sm" onClick={onClose} />
      <div className="absolute inset-y-3 left-3 flex w-[min(20rem,calc(100%-1.5rem))] flex-col rounded-4xl bg-frame p-4 shadow-float">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CompanyLogo className="h-8 w-8" />
            <div>
              <div className="max-w-[12rem] truncate text-lg font-semibold text-ink">{activeBusiness?.businessName ?? "Inventory"}</div>
              {activeBusiness && <div className="text-xs font-medium uppercase tracking-[0.14em] text-ink-3">Inventory</div>}
            </div>
          </div>
          <button onClick={onClose} aria-label="Close menu" className="flex h-10 w-10 items-center justify-center rounded-xl bg-surface-2 text-ink-2">
            <X className="h-5 w-5" />
          </button>
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto">
          {[...NAV.filter((n) => can(n.need)), { label: "Settings", href: "/settings", icon: Settings }].map(({ label, href, icon: Icon }) => {
            const active = isActive(pathname, href);
            return (
              <Link
                key={href}
                href={href}
                className={cn(
                  "flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-medium",
                  active ? "bg-brand-strong text-white" : "text-ink-2 hover:bg-surface-2"
                )}
              >
                <Icon className="h-5 w-5" /> {label}
              </Link>
            );
          })}
        </nav>
      </div>
    </div>
  );
}
