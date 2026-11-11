export const defaultBaseUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:5001";

export const urls = {
  login:   { meth: "POST", url: "auth" },
  register:{ meth: "POST", url: "auth" },
  refresh: { meth: "GET",  url: "auth/refresh" },
  logout:  { meth: "POST", url: "auth/logout" },
};

export const NAV_ITEMS = [
  { label: "Overview",   href: "/overview",   icon: "LayoutDashboard" },
  { label: "Products",   href: "/products",   icon: "Package" },
  { label: "Categories", href: "/categories", icon: "Tag" },
  { label: "Inventory",  href: "/inventory",  icon: "Warehouse" },
  { label: "Sales",      href: "/sales",      icon: "ShoppingCart" },
  { label: "Purchases",  href: "/purchases",  icon: "Truck" },
  { label: "Customers",  href: "/customers",  icon: "Users" },
  { label: "Suppliers",  href: "/suppliers",  icon: "Building2" },
  { label: "Settings",   href: "/settings",   icon: "Settings" },
];

export const PAYMENT_METHODS = [
  { value: "CASH",          label: "Cash" },
  { value: "BANK_TRANSFER", label: "Bank Transfer" },
  { value: "MOBILE_MONEY",  label: "Mobile Money" },
  { value: "PAYSTACK",      label: "Paystack" },
  { value: "EMERGENT",      label: "Emergent" },
  { value: "CREDIT",        label: "Credit (Pay Later)" },
] as const;
