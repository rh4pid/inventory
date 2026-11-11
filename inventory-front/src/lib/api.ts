import type {
  LoginPayload, LoginResponse, User, BusinessMembership,
  Product, ProductPayload, Category, CategoryPayload,
  Supplier, SupplierPayload, Customer, CustomerPayload,
  Inventory, StockInPayload, StockOutPayload, StockTransferPayload, StockAdjustmentPayload,
  Invoice, SalePayload, PurchasePayload, Payment, PaymentPayload,
  Account, AccountPayload, PageResponse, Shop,
  Employee, EmployeePayload, BusinessUser,
} from "@/Types";

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:5001";

let _token: string | null = null;
export const setToken = (t: string | null) => { _token = t; };
export const getToken = () => _token;

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function req<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers as HeadersInit);
  if (_token) headers.set("Authorization", `Bearer ${_token}`);
  if (!(init.body instanceof FormData) && init.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(`${BASE}${path}`, {
    ...init, headers, credentials: "include",
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new ApiError(res.status, err.message ?? res.statusText);
  }
  const contentType = res.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) return res.json() as Promise<T>;
  const text = await res.text();
  return (text || null) as unknown as T;
}

function qs(params?: Record<string, string | number | boolean | undefined>): string {
  if (!params) return "";
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => v != null && p.set(k, String(v)));
  const s = p.toString();
  return s ? `?${s}` : "";
}

// ── Auth ──────────────────────────────────────────────────────────────────────
export const auth = {
  login: (data: LoginPayload) =>
    req<LoginResponse>("/auth", { method: "POST", body: JSON.stringify(data) }),
  register: (formData: FormData) =>
    req<string>("/auth", { method: "POST", body: formData }),
  refresh: () => req<LoginResponse>("/auth/refresh"),
  logout: () => req<string>("/auth/logout", { method: "POST" }),
};

// ── Users ─────────────────────────────────────────────────────────────────────
export const users = {
  me: () => req<User>("/users/me"),
  businesses: () => req<BusinessMembership[]>("/users/me/businesses"),
  updateProfile: (data: Partial<User>) =>
    req<User>("/users/me", { method: "PUT", body: JSON.stringify(data) }),
  lookup: (q: string) => req<User>(`/users/lookup${qs({ q })}`),
};

// ── Shops ────────────────────────────────────────────────────────────────────
export const shops = {
  list: (businessId: number) =>
    req<PageResponse<Shop>>(`/business/${businessId}/shops`),
};

// ── Products ─────────────────────────────────────────────────────────────────
export const products = {
  list: (businessId: number, params?: { page?: number; size?: number; search?: string }) =>
    req<PageResponse<Product>>(`/businesses/${businessId}/products${qs(params)}`),
  get: (businessId: number, id: number) =>
    req<Product>(`/businesses/${businessId}/products/${id}`),
  create: (businessId: number, data: ProductPayload) =>
    req<Product>(`/businesses/${businessId}/products`, { method: "POST", body: JSON.stringify(data) }),
  update: (businessId: number, id: number, data: ProductPayload) =>
    req<Product>(`/businesses/${businessId}/products/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: (businessId: number, id: number) =>
    req<void>(`/businesses/${businessId}/products/${id}`, { method: "DELETE" }),
};

// ── Categories ───────────────────────────────────────────────────────────────
export const categories = {
  list: (businessId: number) =>
    req<Category[]>(`/businesses/${businessId}/categories`),
  create: (businessId: number, data: CategoryPayload) =>
    req<Category>(`/businesses/${businessId}/categories`, { method: "POST", body: JSON.stringify(data) }),
  update: (businessId: number, id: number, data: CategoryPayload) =>
    req<Category>(`/businesses/${businessId}/categories/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: (businessId: number, id: number) =>
    req<void>(`/businesses/${businessId}/categories/${id}`, { method: "DELETE" }),
};

// ── Suppliers ────────────────────────────────────────────────────────────────
export const suppliers = {
  list: (businessId: number, params?: { page?: number; size?: number }) =>
    req<PageResponse<Supplier>>(`/businesses/${businessId}/suppliers${qs(params)}`),
  get: (businessId: number, id: number) =>
    req<Supplier>(`/businesses/${businessId}/suppliers/${id}`),
  create: (businessId: number, data: SupplierPayload) =>
    req<Supplier>(`/businesses/${businessId}/suppliers`, { method: "POST", body: JSON.stringify(data) }),
  update: (businessId: number, id: number, data: SupplierPayload) =>
    req<Supplier>(`/businesses/${businessId}/suppliers/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: (businessId: number, id: number) =>
    req<void>(`/businesses/${businessId}/suppliers/${id}`, { method: "DELETE" }),
};

// ── Customers ────────────────────────────────────────────────────────────────
export const customers = {
  list: (businessId: number, params?: { page?: number; size?: number }) =>
    req<PageResponse<Customer>>(`/businesses/${businessId}/customers${qs(params)}`),
  get: (businessId: number, id: number) =>
    req<Customer>(`/businesses/${businessId}/customers/${id}`),
  create: (businessId: number, data: CustomerPayload) =>
    req<Customer>(`/businesses/${businessId}/customers`, { method: "POST", body: JSON.stringify(data) }),
  update: (businessId: number, id: number, data: CustomerPayload) =>
    req<Customer>(`/businesses/${businessId}/customers/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: (businessId: number, id: number) =>
    req<void>(`/businesses/${businessId}/customers/${id}`, { method: "DELETE" }),
};

// ── Inventory ────────────────────────────────────────────────────────────────
export const inventory = {
  listByShop: (businessId: number, shopId: number, params?: { page?: number; size?: number }) =>
    req<PageResponse<Inventory>>(`/businesses/${businessId}/shops/${shopId}/inventory${qs(params)}`),
  lowStock: (businessId: number) =>
    req<Inventory[]>(`/businesses/${businessId}/inventory/low-stock`),
  stockIn: (businessId: number, data: StockInPayload) =>
    req<Inventory>(`/businesses/${businessId}/shops/${data.shopId}/inventory/stock-in`, {
      method: "POST", body: JSON.stringify(data),
    }),
  stockOut: (businessId: number, data: StockOutPayload) =>
    req<Inventory>(`/businesses/${businessId}/shops/${data.shopId}/inventory/stock-out`, {
      method: "POST", body: JSON.stringify(data),
    }),
  transfer: (businessId: number, data: StockTransferPayload) =>
    req<{ from: Inventory; to: Inventory }>(`/businesses/${businessId}/inventory/transfer`, {
      method: "POST", body: JSON.stringify(data),
    }),
  adjust: (businessId: number, data: StockAdjustmentPayload) =>
    req<Inventory>(`/businesses/${businessId}/shops/${data.shopId}/inventory/adjust`, {
      method: "POST", body: JSON.stringify(data),
    }),
};

// ── Sales ────────────────────────────────────────────────────────────────────
export const sales = {
  list: (businessId: number, params?: { page?: number; size?: number }) =>
    req<PageResponse<Invoice>>(`/businesses/${businessId}/sales${qs(params)}`),
  get: (businessId: number, id: number) =>
    req<Invoice>(`/businesses/${businessId}/sales/${id}`),
  create: (businessId: number, data: SalePayload) =>
    req<Invoice>(`/businesses/${businessId}/sales`, { method: "POST", body: JSON.stringify(data) }),
  void: (businessId: number, id: number) =>
    req<Invoice>(`/businesses/${businessId}/sales/${id}/void`, { method: "POST" }),
  recordPayment: (businessId: number, invoiceId: number, data: PaymentPayload) =>
    req<Payment>(`/businesses/${businessId}/sales/${invoiceId}/payments`, {
      method: "POST", body: JSON.stringify(data),
    }),
  listPayments: (businessId: number, invoiceId: number) =>
    req<Payment[]>(`/businesses/${businessId}/sales/${invoiceId}/payments`),
};

// ── Purchases ────────────────────────────────────────────────────────────────
export const purchases = {
  list: (businessId: number, params?: { page?: number; size?: number }) =>
    req<PageResponse<Invoice>>(`/businesses/${businessId}/purchases${qs(params)}`),
  get: (businessId: number, id: number) =>
    req<Invoice>(`/businesses/${businessId}/purchases/${id}`),
  create: (businessId: number, data: PurchasePayload) =>
    req<Invoice>(`/businesses/${businessId}/purchases`, { method: "POST", body: JSON.stringify(data) }),
  void: (businessId: number, id: number) =>
    req<Invoice>(`/businesses/${businessId}/purchases/${id}/void`, { method: "POST" }),
  recordPayment: (businessId: number, invoiceId: number, data: PaymentPayload) =>
    req<Payment>(`/businesses/${businessId}/purchases/${invoiceId}/payments`, {
      method: "POST", body: JSON.stringify(data),
    }),
};

// ── Employees / HR ───────────────────────────────────────────────────────────
export const employees = {
  list: (businessId: number, params?: { page?: number; size?: number }) =>
    req<PageResponse<Employee>>(`/businesses/${businessId}/employees${qs(params)}`),
  get: (businessId: number, id: number) =>
    req<Employee>(`/businesses/${businessId}/employees/${id}`),
  create: (businessId: number, data: EmployeePayload) =>
    req<Employee>(`/businesses/${businessId}/employees`, { method: "POST", body: JSON.stringify(data) }),
  update: (businessId: number, id: number, data: Partial<EmployeePayload>) =>
    req<Employee>(`/businesses/${businessId}/employees/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  terminate: (businessId: number, id: number) =>
    req<void>(`/businesses/${businessId}/employees/${id}/terminate`, { method: "POST" }),
  listStaff: (businessId: number, params?: { page?: number; size?: number }) =>
    req<PageResponse<BusinessUser>>(`/businesses/${businessId}/staff${qs(params)}`),
};

// ── Accounts ─────────────────────────────────────────────────────────────────
export const accounts = {
  list: (businessId: number) =>
    req<Account[]>(`/businesses/${businessId}/accounts`),
  setupDefaults: (businessId: number) =>
    req<Account[]>(`/businesses/${businessId}/accounts/setup-defaults`, { method: "POST" }),
  create: (businessId: number, data: AccountPayload) =>
    req<Account>(`/businesses/${businessId}/accounts`, { method: "POST", body: JSON.stringify(data) }),
  update: (businessId: number, id: number, data: AccountPayload) =>
    req<Account>(`/businesses/${businessId}/accounts/${id}`, { method: "PUT", body: JSON.stringify(data) }),
};
