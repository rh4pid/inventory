"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard, Package, Tag, Warehouse, ShoppingCart,
  Truck, Users, Building2, Settings, ChevronDown, BoxesIcon,
  ScanLine, UserCog,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useBusiness } from "@/context/BusinessContext";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent,
  DropdownMenuItem, DropdownMenuSeparator, DropdownMenuLabel,
} from "@/components/ui/dropdown-menu";

const ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  LayoutDashboard, Package, Tag, Warehouse, ShoppingCart,
  Truck, Users, Building2, Settings, ScanLine, UserCog,
};

const NAV = [
  { label: "Overview",   href: "/overview",   icon: "LayoutDashboard" },
  { label: "POS",        href: "/pos",        icon: "ScanLine" },
  { label: "Products",   href: "/products",   icon: "Package" },
  { label: "Categories", href: "/categories", icon: "Tag" },
  { label: "Inventory",  href: "/inventory",  icon: "Warehouse" },
  { label: "Sales",      href: "/sales",      icon: "ShoppingCart" },
  { label: "Purchases",  href: "/purchases",  icon: "Truck" },
  { label: "Customers",  href: "/customers",  icon: "Users" },
  { label: "Suppliers",  href: "/suppliers",  icon: "Building2" },
  { label: "Payroll",    href: "/payroll",    icon: "UserCog" },
  { label: "Settings",   href: "/settings",   icon: "Settings" },
];

export function Sidebar() {
  const pathname = usePathname();
  const { businesses, activeBusiness, switchBusiness } = useBusiness();

  return (
    <aside className="flex h-full w-64 flex-col bg-slate-900 text-slate-100">
      {/* Logo */}
      <div className="flex h-16 items-center gap-2 px-5 border-b border-slate-700">
        <BoxesIcon className="h-7 w-7 text-blue-400 shrink-0" />
        <span className="text-lg font-bold tracking-tight">Inventra</span>
      </div>

      {/* Business Switcher */}
      {activeBusiness && (
        <div className="px-3 pt-4 pb-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 hover:bg-slate-800 transition-colors text-left">
                <Avatar className="h-8 w-8 shrink-0">
                  {activeBusiness.businessLogo && (
                    <AvatarImage src={activeBusiness.businessLogo} />
                  )}
                  <AvatarFallback className="text-xs bg-blue-600">
                    {activeBusiness.businessName.slice(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{activeBusiness.businessName}</p>
                  <p className="text-xs text-slate-400 truncate">{activeBusiness.role}</p>
                </div>
                <ChevronDown className="h-4 w-4 text-slate-400 shrink-0" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent className="w-56 ml-2" align="start">
              <DropdownMenuLabel>Switch Business</DropdownMenuLabel>
              <DropdownMenuSeparator />
              {businesses.map((b) => (
                <DropdownMenuItem key={b.businessId} onClick={() => switchBusiness(b)}>
                  <div className="flex flex-col">
                    <span className="font-medium">{b.businessName}</span>
                    <span className="text-xs text-gray-500">{b.role}</span>
                  </div>
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      )}

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto px-3 py-2 space-y-0.5">
        {NAV.map((item) => {
          const Icon = ICONS[item.icon];
          const isActive = pathname === item.href || pathname.startsWith(item.href + "/");
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                isActive
                  ? "bg-blue-600 text-white"
                  : "text-slate-300 hover:bg-slate-800 hover:text-white"
              )}
            >
              {Icon && <Icon className="h-4 w-4 shrink-0" />}
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
