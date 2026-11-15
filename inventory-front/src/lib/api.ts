import type {
  LoginPayload, LoginResponse, SignUpPayload, UsernameCheck, PublicProfile, NewCompanyPayload, ImportKind, ImportResult, User, BusinessMembership,
  Product, ProductPayload, Category, CategoryPayload,
  Supplier, SupplierPayload, Customer, CustomerPayload,
  Inventory, StockInPayload, StockOutPayload, StockTransferPayload, StockAdjustmentPayload,
  Invoice, SalePayload, PurchasePayload, Payment, PaymentPayload,
  Account, AccountPayload, PageResponse, Shop, LocationStaff,
  Employee, EmployeePayload, BusinessUser,
  ShopPayload, Unit, ProductUnit, ProductUnitPayload, StockMovement, StockBatch,
  StockActionResponse, StockRequest, StockRequestStatus, StockCount, StockCountView,
  BusinessSettings, BusinessSettingsPayload, BusinessTypeOption,
  InvoiceDetailView, ReturnPayload, PaymentMethod, DashboardData,
  BusinessProfile, Company, CompanyResult, CreateCompanyPayload, NewPerson, TeamList, TeamMember, TeamAdded, NewTeamMember, UserRole,
  InvitePayload, SentInvitation, MyInvitation,
  AccountingSummary, AccountRef, JournalView, JournalType, Ledger, TrialBalance, ProfitAndLoss, BalanceSheet, CatchUpResult,
  OwnerMoneyPayload, TransferPayload, ManualJournalPayload, Expense, ExpenseOptions, ExpensePayload, ExpenseStatus,
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

/** Downloads a file from the API (with the session) and saves it under {@code filename}. */
async function download(path: string, filename: string): Promise<void> {
  const headers = new Headers();
  if (_token) headers.set("Authorization", `Bearer ${_token}`);
  const res = await fetch(`${BASE}${path}`, { headers, credentials: "include" });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new ApiError(res.status, err.message ?? res.statusText);
  }
  saveBlob(await res.blob(), filename);
}

/** Saves data the app already has (e.g. a CSV it built) as a file. */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
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
  signUp: (data: SignUpPayload) =>
    req<LoginResponse>("/auth/signup", { method: "POST", body: JSON.stringify(data) }),
  usernameAvailable: (username: string) => req<UsernameCheck>(`/auth/username-available${qs({ username })}`),
  refresh: () => req<LoginResponse>("/auth/refresh"),
  logout: () => req<string>("/auth/logout", { method: "POST" }),
};

// ── Users ─────────────────────────────────────────────────────────────────────
export const users = {
  me: () => req<User>("/users/me"),
  businesses: () => req<BusinessMembership[]>("/users/me/businesses"),
  updateProfile: (data: Partial<User>) =>
    req<User>("/users/me", { method: "PUT", body: JSON.stringify(data) }),
  /** Find a person by exact username (public details only). */
  lookup: (username: string) => req<PublicProfile>(`/users/lookup${qs({ q: username })}`),
  changePassword: (currentPassword: string, newPassword: string) =>
    req<User>("/users/me/password", { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) }),
  /** Invitations waiting for your answer. */
  invitations: () => req<MyInvitation[]>("/users/me/invitations"),
  acceptInvitation: (id: number) => req<BusinessMembership>(`/users/me/invitations/${id}/accept`, { method: "POST" }),
  declineInvitation: (id: number) => req<void>(`/users/me/invitations/${id}/decline`, { method: "POST" }),
};

// ── Spreadsheet uploads (all or nothing) ─────────────────────────────────────
export const imports = {
  template: (businessId: number, kind: ImportKind) =>
    download(`/businesses/${businessId}/imports/${kind}/template`, `${kind}-template.xlsx`),
  /** check=true only checks the file; otherwise it's saved if every row is fine. */
  upload: (businessId: number, kind: ImportKind, file: File, check: boolean) => {
    const body = new FormData();
    body.append("file", file);
    return req<ImportResult>(`/businesses/${businessId}/imports/${kind}${qs({ check })}`, { method: "POST", body });
  },
};

// ── Your own companies ───────────────────────────────────────────────────────
export const companies = {
  /** Create a company; you become its owner. Returns your new membership. */
  create: (data: NewCompanyPayload) =>
    req<BusinessMembership>("/companies", { method: "POST", body: JSON.stringify(data) }),
};

// ── Shops ────────────────────────────────────────────────────────────────────
// Shops are stock locations of any kind (shop, warehouse, storeroom…).
export const shops = {
  /** With mine: true, only the locations the current user works at (all of them for managers). */
  list: (businessId: number, params?: { page?: number; size?: number; mine?: boolean }) =>
    req<PageResponse<Shop>>(`/general/business/${businessId}/shops${qs({ size: 100, ...params })}`),
  staff: (businessId: number, shopId: number) =>
    req<LocationStaff[]>(`/businesses/${businessId}/shops/${shopId}/staff`),
  assignStaff: (businessId: number, shopId: number, businessUserId: number) =>
    req<LocationStaff>(`/businesses/${businessId}/shops/${shopId}/staff`, {
      method: "POST", body: JSON.stringify({ businessUserId }),
    }),
  unassignStaff: (businessId: number, shopId: number, assignmentId: number) =>
    req<void>(`/businesses/${businessId}/shops/${shopId}/staff/${assignmentId}`, { method: "DELETE" }),
  create: (data: ShopPayload) =>
    req<unknown>(`/general/shop`, { method: "POST", body: JSON.stringify(data) }),
  update: (shopId: number, data: ShopPayload) =>
    req<unknown>(`/general/shop/${shopId}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: (shopId: number) =>
    req<unknown>(`/general/shop/${shopId}`, { method: "DELETE" }),
};

// ── Business settings & reference data ──────────────────────────────────────
export const settings = {
  get: (businessId: number) =>
    req<BusinessSettings>(`/businesses/${businessId}/settings`),
  update: (businessId: number, data: BusinessSettingsPayload) =>
    req<BusinessSettings>(`/businesses/${businessId}/settings`, { method: "PUT", body: JSON.stringify(data) }),
  businessTypes: () => req<BusinessTypeOption[]>(`/reference/business-types`),
  profile: (businessId: number) => req<BusinessProfile>(`/businesses/${businessId}/profile`),
  updateProfile: (businessId: number, data: Omit<BusinessProfile, "id" | "code" | "logo">) =>
    req<BusinessProfile>(`/businesses/${businessId}/profile`, { method: "PUT", body: JSON.stringify(data) }),
};

// ── Units of measure & product pack sizes ───────────────────────────────────
export const units = {
  list: (businessId: number) =>
    req<Unit[]>(`/businesses/${businessId}/units`),
  create: (businessId: number, data: { name: string; symbol?: string }) =>
    req<Unit>(`/businesses/${businessId}/units`, { method: "POST", body: JSON.stringify(data) }),
  update: (businessId: number, id: number, data: { name: string; symbol?: string }) =>
    req<Unit>(`/businesses/${businessId}/units/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: (businessId: number, id: number) =>
    req<void>(`/businesses/${businessId}/units/${id}`, { method: "DELETE" }),
  forProduct: (businessId: number, productId: number) =>
    req<ProductUnit[]>(`/businesses/${businessId}/products/${productId}/units`),
  setForProduct: (businessId: number, productId: number, data: ProductUnitPayload[]) =>
    req<ProductUnit[]>(`/businesses/${businessId}/products/${productId}/units`, {
      method: "PUT", body: JSON.stringify(data),
    }),
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
  /** Sets the product's picture (send one already shrunk with shrinkImage). */
  uploadImage: (businessId: number, id: number, image: Blob) => {
    const body = new FormData();
    body.append("file", image, "product.jpg");
    return req<Product>(`/businesses/${businessId}/products/${id}/image`, { method: "POST", body });
  },
  removeImage: (businessId: number, id: number) =>
    req<Product>(`/businesses/${businessId}/products/${id}/image`, { method: "DELETE" }),
};

/** Public link of a product picture (null when it has none). */
export const productImageUrl = (key?: string | null) => (key ? `${BASE}/public/product-images/${key}` : null);

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
  batches: (businessId: number, params?: { shopId?: number; productId?: number; includeEmpty?: boolean }) =>
    req<StockBatch[]>(`/businesses/${businessId}/inventory/batches${qs(params)}`),
  expiring: (businessId: number, params?: { shopId?: number; days?: number }) =>
    req<StockBatch[]>(`/businesses/${businessId}/inventory/expiring${qs(params)}`),
  movements: (businessId: number, params?: { shopId?: number; productId?: number; page?: number; size?: number }) =>
    req<PageResponse<StockMovement>>(`/businesses/${businessId}/inventory/movements${qs(params)}`),
  stockIn: (businessId: number, data: StockInPayload) =>
    req<Inventory>(`/businesses/${businessId}/shops/${data.shopId}/inventory/stock-in`, {
      method: "POST", body: JSON.stringify(data),
    }),
  stockOut: (businessId: number, data: StockOutPayload) =>
    req<StockActionResponse>(`/businesses/${businessId}/shops/${data.shopId}/inventory/stock-out`, {
      method: "POST", body: JSON.stringify(data),
    }),
  transfer: (businessId: number, data: StockTransferPayload) =>
    req<{ from: Inventory; to: Inventory }>(`/businesses/${businessId}/inventory/transfer`, {
      method: "POST", body: JSON.stringify(data),
    }),
  adjust: (businessId: number, data: StockAdjustmentPayload) =>
    req<StockActionResponse>(`/businesses/${businessId}/shops/${data.shopId}/inventory/adjust`, {
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
  /** Works on paid invoices too: stock goes back, payments are refunded, the books are reversed. */
  void: (businessId: number, id: number, refundMethod?: PaymentMethod) =>
    req<Invoice>(`/businesses/${businessId}/sales/${id}/void`, {
      method: "POST", body: JSON.stringify({ refundMethod }),
    }),
  detail: (businessId: number, id: number) =>
    req<InvoiceDetailView>(`/businesses/${businessId}/sales/${id}/detail`),
  returnItems: (businessId: number, id: number, data: ReturnPayload) =>
    req<InvoiceDetailView>(`/businesses/${businessId}/sales/${id}/returns`, {
      method: "POST", body: JSON.stringify(data),
    }),
  recordPayment: (businessId: number, invoiceId: number, data: PaymentPayload) =>
    req<Payment>(`/businesses/${businessId}/sales/${invoiceId}/payments`, {
      method: "POST", body: JSON.stringify(data),
    }),
  listPayments: (businessId: number, invoiceId: number) =>
    req<Payment[]>(`/businesses/${businessId}/sales/${invoiceId}/payments`),
};

// ── Approvals (stock-outs / adjustments waiting for a manager) ────────────────
export const stockRequests = {
  list: (businessId: number, status?: StockRequestStatus) =>
    req<StockRequest[]>(`/businesses/${businessId}/stock-requests${qs({ status })}`),
  pendingCount: (businessId: number) =>
    req<{ count: number }>(`/businesses/${businessId}/stock-requests/pending-count`),
  approve: (businessId: number, id: number, note?: string) =>
    req<StockRequest>(`/businesses/${businessId}/stock-requests/${id}/approve`, {
      method: "POST", body: JSON.stringify({ note }),
    }),
  reject: (businessId: number, id: number, note?: string) =>
    req<StockRequest>(`/businesses/${businessId}/stock-requests/${id}/reject`, {
      method: "POST", body: JSON.stringify({ note }),
    }),
  cancel: (businessId: number, id: number) =>
    req<StockRequest>(`/businesses/${businessId}/stock-requests/${id}/cancel`, { method: "POST" }),
};

// ── Stock counts (stocktakes) ────────────────────────────────────────────────
export const stockCounts = {
  list: (businessId: number) =>
    req<StockCount[]>(`/businesses/${businessId}/stock-counts`),
  start: (businessId: number, shopId: number, notes?: string) =>
    req<StockCountView>(`/businesses/${businessId}/stock-counts`, {
      method: "POST", body: JSON.stringify({ shopId, notes }),
    }),
  get: (businessId: number, id: number) =>
    req<StockCountView>(`/businesses/${businessId}/stock-counts/${id}`),
  addProduct: (businessId: number, id: number, productId: number) =>
    req<StockCountView>(`/businesses/${businessId}/stock-counts/${id}/lines`, {
      method: "POST", body: JSON.stringify({ productId }),
    }),
  saveCounts: (businessId: number, id: number, entries: { lineId: number; countedQuantity: number | null }[]) =>
    req<StockCountView>(`/businesses/${businessId}/stock-counts/${id}/lines`, {
      method: "PUT", body: JSON.stringify(entries),
    }),
  complete: (businessId: number, id: number) =>
    req<StockCountView>(`/businesses/${businessId}/stock-counts/${id}/complete`, { method: "POST" }),
  reopen: (businessId: number, id: number) =>
    req<StockCountView>(`/businesses/${businessId}/stock-counts/${id}/reopen`, { method: "POST" }),
  cancel: (businessId: number, id: number) =>
    req<StockCountView>(`/businesses/${businessId}/stock-counts/${id}/cancel`, { method: "POST" }),
};

// ── Purchases ────────────────────────────────────────────────────────────────
export const purchases = {
  list: (businessId: number, params?: { page?: number; size?: number }) =>
    req<PageResponse<Invoice>>(`/businesses/${businessId}/purchases${qs(params)}`),
  get: (businessId: number, id: number) =>
    req<Invoice>(`/businesses/${businessId}/purchases/${id}`),
  create: (businessId: number, data: PurchasePayload) =>
    req<Invoice>(`/businesses/${businessId}/purchases`, { method: "POST", body: JSON.stringify(data) }),
  /** Works on paid invoices too: stock goes back, payments are refunded, the books are reversed. */
  void: (businessId: number, id: number, refundMethod?: PaymentMethod) =>
    req<Invoice>(`/businesses/${businessId}/purchases/${id}/void`, {
      method: "POST", body: JSON.stringify({ refundMethod }),
    }),
  detail: (businessId: number, id: number) =>
    req<InvoiceDetailView>(`/businesses/${businessId}/purchases/${id}/detail`),
  returnItems: (businessId: number, id: number, data: ReturnPayload) =>
    req<InvoiceDetailView>(`/businesses/${businessId}/purchases/${id}/returns`, {
      method: "POST", body: JSON.stringify(data),
    }),
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

// ── Dashboard ────────────────────────────────────────────────────────────────
export const dashboard = {
  /** Figures for one location (or all my locations) over a period. */
  get: (businessId: number, params?: { shopId?: number; period?: string }) =>
    req<DashboardData>(`/businesses/${businessId}/dashboard${qs(params)}`),
};

// ── Super admin console ──────────────────────────────────────────────────────
export const admin = {
  companies: () => req<Company[]>("/admin/companies"),
  company: (id: number) => req<Company>(`/admin/companies/${id}`),
  createCompany: (data: CreateCompanyPayload) =>
    req<CompanyResult>("/admin/companies", { method: "POST", body: JSON.stringify(data) }),
  updateCompany: (id: number, data: { name?: string; email?: string; contact?: string }) =>
    req<Company>(`/admin/companies/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  suspend: (id: number) => req<Company>(`/admin/companies/${id}/suspend`, { method: "POST" }),
  reactivate: (id: number) => req<Company>(`/admin/companies/${id}/reactivate`, { method: "POST" }),
  addAdmin: (id: number, data: NewPerson) =>
    req<CompanyResult>(`/admin/companies/${id}/admins`, { method: "POST", body: JSON.stringify(data) }),
  resetAdminPassword: (id: number, membershipId: number) =>
    req<CompanyResult>(`/admin/companies/${id}/admins/${membershipId}/reset-password`, { method: "POST" }),
};

// ── Team (a company's people) ────────────────────────────────────────────────
export const team = {
  list: (businessId: number) => req<TeamList>(`/businesses/${businessId}/team`),
  add: (businessId: number, data: NewTeamMember) =>
    req<TeamAdded>(`/businesses/${businessId}/team`, { method: "POST", body: JSON.stringify(data) }),
  update: (businessId: number, memberId: number, data: { role?: UserRole; shopIds?: number[] }) =>
    req<TeamMember>(`/businesses/${businessId}/team/${memberId}`, { method: "PUT", body: JSON.stringify(data) }),
  resetPassword: (businessId: number, memberId: number) =>
    req<TeamAdded>(`/businesses/${businessId}/team/${memberId}/reset-password`, { method: "POST" }),
  remove: (businessId: number, memberId: number) =>
    req<void>(`/businesses/${businessId}/team/${memberId}`, { method: "DELETE" }),
  /** Invite someone who already has an account; they join when they accept. */
  invite: (businessId: number, data: InvitePayload) =>
    req<SentInvitation>(`/businesses/${businessId}/team/invitations`, { method: "POST", body: JSON.stringify(data) }),
  resendInvitation: (businessId: number, invitationId: number) =>
    req<SentInvitation>(`/businesses/${businessId}/team/invitations/${invitationId}/resend`, { method: "POST" }),
  cancelInvitation: (businessId: number, invitationId: number) =>
    req<void>(`/businesses/${businessId}/team/invitations/${invitationId}`, { method: "DELETE" }),
};

// ── Accounting: the books and reports (admins) ───────────────────────────────
export const accounting = {
  summary: (businessId: number) => req<AccountingSummary>(`/businesses/${businessId}/accounting/summary`),
  moneyAccounts: (businessId: number) => req<AccountRef[]>(`/businesses/${businessId}/accounting/money-accounts`),
  capital: (businessId: number, data: OwnerMoneyPayload) =>
    req<JournalView>(`/businesses/${businessId}/accounting/capital`, { method: "POST", body: JSON.stringify(data) }),
  drawings: (businessId: number, data: OwnerMoneyPayload) =>
    req<JournalView>(`/businesses/${businessId}/accounting/drawings`, { method: "POST", body: JSON.stringify(data) }),
  transfer: (businessId: number, data: TransferPayload) =>
    req<JournalView>(`/businesses/${businessId}/accounting/transfers`, { method: "POST", body: JSON.stringify(data) }),
  journals: (businessId: number, params?: { type?: JournalType; from?: string; to?: string; page?: number; size?: number }) =>
    req<PageResponse<JournalView>>(`/businesses/${businessId}/accounting/journals${qs(params)}`),
  manualJournal: (businessId: number, data: ManualJournalPayload) =>
    req<JournalView>(`/businesses/${businessId}/accounting/journals`, { method: "POST", body: JSON.stringify(data) }),
  ledger: (businessId: number, accountId: number, params?: { from?: string; to?: string }) =>
    req<Ledger>(`/businesses/${businessId}/accounting/ledger/${accountId}${qs(params)}`),
  trialBalance: (businessId: number, asOf?: string) =>
    req<TrialBalance>(`/businesses/${businessId}/accounting/reports/trial-balance${qs({ asOf })}`),
  profitLoss: (businessId: number, params?: { from?: string; to?: string }) =>
    req<ProfitAndLoss>(`/businesses/${businessId}/accounting/reports/profit-loss${qs(params)}`),
  balanceSheet: (businessId: number, asOf?: string) =>
    req<BalanceSheet>(`/businesses/${businessId}/accounting/reports/balance-sheet${qs({ asOf })}`),
  catchUp: (businessId: number) =>
    req<CatchUpResult>(`/businesses/${businessId}/accounting/catch-up`, { method: "POST" }),
};

// ── Expenses (petty cash; cashiers' need a manager's approval) ───────────────
export const expenses = {
  list: (businessId: number, params?: { status?: ExpenseStatus; shopId?: number; page?: number; size?: number }) =>
    req<PageResponse<Expense>>(`/businesses/${businessId}/expenses${qs(params)}`),
  options: (businessId: number) => req<ExpenseOptions>(`/businesses/${businessId}/expenses/options`),
  pendingCount: (businessId: number) => req<{ count: number }>(`/businesses/${businessId}/expenses/pending-count`),
  create: (businessId: number, data: ExpensePayload) =>
    req<Expense>(`/businesses/${businessId}/expenses`, { method: "POST", body: JSON.stringify(data) }),
  approve: (businessId: number, id: number, note?: string) =>
    req<Expense>(`/businesses/${businessId}/expenses/${id}/approve`, { method: "POST", body: JSON.stringify({ note }) }),
  reject: (businessId: number, id: number, note?: string) =>
    req<Expense>(`/businesses/${businessId}/expenses/${id}/reject`, { method: "POST", body: JSON.stringify({ note }) }),
  cancel: (businessId: number, id: number) =>
    req<Expense>(`/businesses/${businessId}/expenses/${id}/cancel`, { method: "POST" }),
  void: (businessId: number, id: number, note?: string) =>
    req<Expense>(`/businesses/${businessId}/expenses/${id}/void`, { method: "POST", body: JSON.stringify({ note }) }),
};
