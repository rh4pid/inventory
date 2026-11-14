// ── Auth ──────────────────────────────────────────────────────────────────────
export interface User {
  id: number;
  firstName: string;
  otherName?: string;
  lastName: string;
  email: string;
  username?: string;
  telephonePrimary?: string;
  telephoneSecondary?: string;
  profilePicture?: string;
  /** Platform operator: manages companies and their admins (Admin console). */
  superAdmin?: boolean;
  /** Signed in with a temporary password someone else set; must choose their own first. */
  mustChangePassword?: boolean;
}

/** login: email address or username. */
export interface LoginPayload { login: string; password: string; }
export interface SignUpPayload {
  firstName: string; otherName?: string; lastName: string;
  email: string; username: string; telephonePrimary?: string; password: string;
}
export interface UsernameCheck { username: string; available: boolean; reason?: string | null; }
/** What anyone may see about a person found by username. */
/** A company you create for yourself (you become its owner). */
export interface NewCompanyPayload { name: string; businessType?: string; email?: string; contact?: string; }
export interface PublicProfile { id: number; username: string; name: string; profilePicture?: string; }
export interface LoginResponse { accessToken: string; user: User; }

// ── Business ──────────────────────────────────────────────────────────────────
export type UserRole = "OWNER" | "ADMIN" | "MANAGER" | "CASHIER" | "STAFF" | "DRIVER" | "SHOPKEEP";

export interface BusinessMembership {
  businessUserId: number;
  businessId: number;
  businessName: string;
  businessCode: string;
  businessEmail?: string;
  businessLogo?: string;
  role: UserRole;
  /** What this role allows; see lib/access.ts. */
  capabilities?: string[];
}

export type LocationType = "SHOP" | "WAREHOUSE" | "STOREROOM" | "PHARMACY" | "VEHICLE" | "OTHER";

export interface Shop {
  id: number;
  shopCode: string;
  shopName: string;
  shopLocation?: string;
  locationType?: LocationType;
  totalProducts?: number;
  active?: boolean;
  deleted?: boolean;
}

// ── Catalog ──────────────────────────────────────────────────────────────────
export interface Category {
  id: number;
  categoryName: string;
  parent?: Category;
  createdAt?: string;
}

export interface Unit { id: number; name: string; symbol?: string; }

/** A pack size a product can be bought or sold in, e.g. Carton = 24 × base unit. */
export interface ProductUnit {
  id: number;
  unit: Unit;
  conversionFactor: number;
  barcode?: string;
  sellingPrice?: number;
}

export interface ProductUnitPayload {
  unitId: number;
  conversionFactor: number;
  barcode?: string;
  sellingPrice?: number;
}

// ── Business settings ─────────────────────────────────────────────────────────
export type BusinessType =
  | "GENERAL_RETAIL" | "PROVISION_SHOP" | "SUPERMARKET" | "PHARMACY"
  | "WAREHOUSE" | "HARDWARE" | "RESTAURANT" | "OTHER";

export interface BusinessSettings {
  /** Who last saved the settings (audit field). */
  updatedBy?: string;
  /** Colour theme id (lib/themes.ts); null = automatic from the business type. */
  theme?: string | null;
  businessType: BusinessType;
  currencyCode: string;
  currencySymbol: string;
  taxEnabled: boolean;
  vatNumber?: string;
  lowStockAlerts: boolean;
  trackBatches: boolean;
  trackExpiry: boolean;
  expiryAlertDays: number;
  multipleUnits: boolean;
  allowNegativeStock: boolean;
  requireAdjustmentApproval: boolean;
}

export type BusinessSettingsPayload = Partial<BusinessSettings> & { applyPreset?: boolean };

export interface BusinessTypeOption {
  value: BusinessType;
  label: string;
  trackBatches: boolean;
  trackExpiry: boolean;
  multipleUnits: boolean;
  requireAdjustmentApproval: boolean;
}

export interface ShopPayload {
  businessId: number;
  shopName: string;
  shopLocation?: string;
  locationType?: LocationType;
  active?: boolean;
}

export interface TaxRate { id: number; name: string; rate: number; active: boolean; }

export interface Product {
  id: number;
  productName: string;
  description?: string;
  sku?: string;
  barcode?: string;
  image?: string;
  costPrice: number;
  sellingPrice: number;
  actualPrice?: number;
  discountType?: string;
  discountValue?: number;
  reorderLevel?: number;
  totalCount: number;
  category?: Category;
  unit?: Unit;
  taxRate?: TaxRate;
  deleted?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProductPayload {
  productName: string;
  description?: string;
  sku?: string;
  barcode?: string;
  costPrice: number;
  sellingPrice: number;
  discountType?: string;
  discountValue?: number;
  reorderLevel?: number;
  categoryId?: number;
  unitId?: number;
  taxRateId?: number;
}

export interface CategoryPayload { categoryName: string; parentId?: number; }

// ── Parties ───────────────────────────────────────────────────────────────────
export interface Supplier {
  id: number;
  companyName: string;
  relationshipOfficer?: string;
  companyEmail?: string;
  rmEmail?: string;
  telephonePrimary?: string;
  telephoneSecondary?: string;
}

export interface SupplierPayload {
  companyName: string;
  relationshipOfficer?: string;
  companyEmail?: string;
  telephonePrimary?: string;
  telephoneSecondary?: string;
}

export interface Customer {
  id: number;
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  address?: string;
  loyaltyPoints: number;
  totalPurchases: number;
}

export interface CustomerPayload {
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  address?: string;
}

// ── Inventory ─────────────────────────────────────────────────────────────────
export interface Inventory {
  id: number;
  product: Product;
  shop: Shop;
  quantity: number;
  minimumQuantity?: number;
  maximumQuantity?: number;
}

export type StockMovementType =
  | "STOCK_IN" | "STOCK_OUT" | "TRANSFER_IN" | "TRANSFER_OUT" | "ADJUSTMENT"
  | "SALE" | "SALE_RETURN" | "PURCHASE_RETURN" | "OPENING_BALANCE";

export interface StockMovement {
  id: number;
  product: Product;
  shop: Shop;
  movementType: StockMovementType;
  quantityBefore: number;
  quantityChange: number;
  quantityAfter: number;
  enteredQuantity?: number;
  enteredUnit?: string;
  batchId?: number;
  batchNumber?: string;
  notes?: string;
  performedBy?: string;
  createdAt?: string;
}

// ── Controls: approvals & stock counts ───────────────────────────────────────
export type StockRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

/** A stock-out or adjustment by a cashier/staff member waiting for a manager. */
export interface StockRequest {
  id: number;
  requestType: "STOCK_OUT" | "ADJUSTMENT";
  status: StockRequestStatus;
  productId: number;
  productName: string;
  baseUnit?: string;
  shopId: number;
  shopName: string;
  summary: string;
  reason?: string;
  notes?: string;
  batchId?: number;
  requestedBy: string;
  createdAt: string;
  decidedBy?: string;
  decidedAt?: string;
  decisionNote?: string;
}

/** Stock-out / adjust either applies now or comes back as a pending request (HTTP 202). */
export type StockActionResponse = Inventory | { pending: true; request: StockRequest };

export const isPending = (r: StockActionResponse): r is { pending: true; request: StockRequest } =>
  typeof r === "object" && r !== null && "pending" in r;

export type StockCountStatus = "OPEN" | "SUBMITTED" | "POSTED" | "CANCELLED";

export interface StockCount {
  id: number;
  reference: string;
  status: StockCountStatus;
  shopId: number;
  shopName: string;
  notes?: string;
  createdBy: string;
  createdAt: string;
  submittedBy?: string;
  submittedAt?: string;
  postedBy?: string;
  postedAt?: string;
}

/** systemQuantity / difference are null in a blind count (non-managers while counting). */
export interface StockCountLine {
  id: number;
  productId: number;
  productName: string;
  sku?: string;
  baseUnit?: string;
  systemQuantity: number | null;
  countedQuantity: number | null;
  difference: number | null;
  postedDifference: number | null;
}

export interface StockCountView {
  count: StockCount;
  blind: boolean;
  canPost: boolean;
  lines: StockCountLine[];
  countedLines: number;
  totalLines: number;
}

export type BatchStatus = "OK" | "EXPIRING" | "EXPIRED" | "NO_EXPIRY";

/** A lot of one product at one location (when the business tracks batches). */
export interface StockBatch {
  id: number;
  productId: number;
  productName: string;
  sku?: string;
  baseUnit?: string;
  shopId: number;
  shopName: string;
  batchNumber: string;
  expiryDate?: string;      // yyyy-MM-dd
  daysToExpiry?: number;
  status: BatchStatus;
  receivedAt: string;
  unitCost?: number;
  quantityReceived: number;
  quantityRemaining: number;
  source?: string;
}

/**
 * unitId: the pack size the quantity is entered in; omit for the product's base unit.
 * batchId: take from / count this batch only; omit to pick automatically.
 * batchNumber / expiryDate: details of the lot being received (batch tracking only).
 */
export interface StockInPayload {
  productId: number;
  shopId: number;
  quantity: number;
  unitId?: number;
  notes?: string;
  supplierId?: number;
  costPrice?: number;
  batchNumber?: string;
  expiryDate?: string;
}

export interface StockOutPayload {
  productId: number;
  shopId: number;
  quantity: number;
  unitId?: number;
  batchId?: number;
  notes?: string;
  reason?: string;
}

export interface StockTransferPayload {
  productId: number;
  fromShopId: number;
  toShopId: number;
  quantity: number;
  unitId?: number;
  batchId?: number;
  notes?: string;
}

export interface StockAdjustmentPayload {
  productId: number;
  shopId: number;
  newQuantity: number;
  batchId?: number;
  batchNumber?: string;
  expiryDate?: string;
  notes?: string;
  reason?: string;
}

// ── Sales & Purchases ─────────────────────────────────────────────────────────
export type InvoiceType = "SALES" | "PURCHASE";
export type InvoiceStatus = "PENDING" | "COMPLETED" | "VOID";
export type PaymentStatus = "UNPAID" | "PARTIAL" | "PAID" | "VOID";
export type PaymentMethod = "CASH" | "BANK_TRANSFER" | "MOBILE_MONEY" | "PAYSTACK" | "EMERGENT" | "CREDIT";

export interface Invoice {
  id: number;
  invoiceNumber: string;
  type: InvoiceType;
  invoiceStatus: InvoiceStatus;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod;
  subtotal: string;
  discountAmount: string;
  taxAmount: string;
  totalAmount: string;
  amountPaid: string;
  returnedAmount?: string;
  customer?: Customer;
  supplier?: Supplier;
  shop?: Shop;
  notes?: string;
  createdAt?: string;
}

export interface SaleItemPayload {
  productId: number;
  quantity: number;
  unitId?: number;
  unitPrice?: number;
  discountAmount?: number;
  batchId?: number;
}

export interface SalePayload {
  shopId: number;
  customerId?: number;
  paymentMethod: PaymentMethod;
  items: SaleItemPayload[];
  discountAmount?: number;
  taxRate?: number;
  notes?: string;
}

export interface PurchaseItemPayload {
  productId: number;
  quantity: number;
  unitId?: number;
  unitPrice: number;
  batchNumber?: string;
  expiryDate?: string;
}

export interface PurchasePayload {
  shopId: number;
  supplierId?: number;
  paymentMethod: PaymentMethod;
  items: PurchaseItemPayload[];
  notes?: string;
}

export interface Payment {
  id: number;
  amount: string;
  paymentMethod: PaymentMethod;
  paymentReference?: string;
  paidAt?: string;
  notes?: string;
}

export interface PaymentPayload {
  amount: number;
  paymentMethod: PaymentMethod;
  paymentReference?: string;
  notes?: string;
}

// ── Accounts ─────────────────────────────────────────────────────────────────
export type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "REVENUE" | "EXPENSE";
export type AccountPurpose =
  | "CASH" | "BANK" | "MOBILE_MONEY" | "PETTY_CASH" | "ACCOUNTS_RECEIVABLE" | "INVENTORY"
  | "ACCOUNTS_PAYABLE" | "CAPITAL" | "DRAWINGS" | "RETAINED_EARNINGS"
  | "SALES_REVENUE" | "OTHER_INCOME" | "COST_OF_GOODS_SOLD" | "PURCHASE"
  | "DISCOUNT_RECEIVED" | "DISCOUNT_ALLOWED" | "STOCK_ADJUSTMENT"
  | "TAX_PAYABLE" | "TAX_RECEIVABLE" | "EXPENSE" | "OTHER";

export interface Account {
  id: number;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  accountPurpose?: AccountPurpose;
  active: boolean;
}

export interface AccountPayload {
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  accountPurpose?: AccountPurpose;
  active?: boolean;
}

// ── Accounting (books & reports) ─────────────────────────────────────────────
export type JournalType =
  | "SALE_CASH" | "SALE_CREDIT" | "PURCHASE_CASH" | "PURCHASE_CREDIT" | "CUSTOMER_PAYMENT" | "SUPPLIER_PAYMENT"
  | "SALE_RETURN" | "PURCHASE_RETURN" | "EXPENSE_CASH" | "EXPENSE_CREDIT" | "STOCK_ADJUSTMENT" | "STOCK_TRANSFER"
  | "OPENING_BALANCE" | "OWNER_CAPITAL" | "OWNER_DRAWINGS" | "FUND_TRANSFER" | "MANUAL" | "OTHER";

export interface AccountRef { id: number; code: string; name: string; type: AccountType; purpose?: AccountPurpose }
export interface AccountBalance { account: AccountRef; balance: number }

export interface AccountingSummary {
  money: AccountBalance[]; totalMoney: number;
  receivables: number; payables: number; stockValue: number; capital: number;
  monthStart: string; monthIncome: number; monthCosts: number; monthProfit: number;
  pendingExpenses: number; invoicesNotInBooks: number;
}

export interface JournalLine { accountId: number; accountCode: string; accountName: string; debit: number; credit: number; description?: string }
export interface JournalView {
  id: number; date: string; reference: string; description: string; type: JournalType;
  shopName?: string; createdBy?: string; total: number; lines: JournalLine[];
}

export interface LedgerLine { journalId: number; date: string; reference: string; description: string; debit: number; credit: number; balance: number }
export interface Ledger {
  account: AccountRef; from: string; to: string; opening: number;
  totalDebit: number; totalCredit: number; closing: number; lines: LedgerLine[];
}

export interface ReportRow { account: AccountRef; amount: number }
export interface TrialBalance {
  asOf: string; rows: { account: AccountRef; debit: number; credit: number }[];
  totalDebit: number; totalCredit: number; balanced: boolean;
}
export interface ProfitAndLoss {
  from: string; to: string;
  income: ReportRow[]; totalIncome: number;
  costOfSales: ReportRow[]; totalCostOfSales: number; grossProfit: number;
  expenses: ReportRow[]; totalExpenses: number; netProfit: number;
}
export interface BalanceSheet {
  asOf: string; assets: ReportRow[]; totalAssets: number;
  liabilities: ReportRow[]; totalLiabilities: number;
  equity: ReportRow[]; profitToDate: number; totalEquity: number;
  totalLiabilitiesAndEquity: number; balanced: boolean;
}
export interface CatchUpResult { invoicesPosted: number; paymentsPosted: number; skipped: number; notes: string[] }

export interface OwnerMoneyPayload { amount: number; accountId?: number; method?: PaymentMethod; date?: string; note?: string }
export interface TransferPayload { fromAccountId: number; toAccountId: number; amount: number; date?: string; note?: string }
export interface ManualJournalPayload {
  date?: string; description: string; reference?: string;
  lines: { accountId: number; debit?: number; credit?: number; memo?: string }[];
}

// ── Expenses (petty cash) ────────────────────────────────────────────────────
export type ExpenseStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED" | "VOID";

export interface Expense {
  id: number; reference: string; date: string; description: string; payee?: string; receiptNumber?: string;
  amount: number; status: ExpenseStatus;
  shopId?: number; shopName?: string; expenseAccountId: number; expenseAccountName: string;
  paidFromId: number; paidFromName: string;
  requestedBy: string; requestedAt: string; decidedBy?: string; decidedAt?: string; decisionNote?: string;
  canApprove: boolean; canCancel: boolean; canVoid: boolean;
}

export interface ExpenseOption { id: number; code: string; name: string; balance?: number | null }
export interface ExpenseOptions {
  expenseAccounts: ExpenseOption[]; paidFrom: ExpenseOption[]; defaultPaidFromId?: number | null;
  branches: { id: number; name: string }[]; needsApproval: boolean; canApprove: boolean;
}

export interface ExpensePayload {
  shopId?: number; expenseAccountId: number; paidFromAccountId?: number; amount: number;
  date?: string; description: string; payee?: string; receiptNumber?: string;
}

// ── Employees / HR ───────────────────────────────────────────────────────────
export type SalaryType = "MONTHLY" | "WEEKLY" | "DAILY" | "HOURLY";

export interface BusinessUser {
  id: number;
  user: User;
  roles: UserRole;
  createdAt?: string;
}

export interface Employee {
  id: number;
  businessUser: BusinessUser;
  position?: string;
  department?: string;
  salary?: number;
  salaryType?: SalaryType;
  startDate?: string;
  endDate?: string;
  createdAt?: string;
}

export interface EmployeePayload {
  businessUserId: number;
  position?: string;
  department?: string;
  salary?: number;
  salaryType?: SalaryType;
  startDate?: string;
  endDate?: string;
}

// ── Pagination ────────────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

/** A staff member assigned to a location. Non-manager staff can only use their assigned locations. */
export interface LocationStaff {
  id: number;            // assignment id
  businessUserId: number;
  name: string;
  email: string;
  role: UserRole;
}

export interface InvoiceLine {
  id: number;
  productId: number;
  productName: string;
  sku?: string;
  quantity: number;
  unitName?: string;        // pack size, if sold/bought in one
  baseQuantity?: number;
  returnedQuantity: number;
  returnable: number;       // still possible to return (0 once void)
  unitPrice: string;
  totalLine: string;
  batchInfo?: string;
}

export interface InvoicePaymentLine {
  id: number;
  amount: string;           // negative = refund
  paymentMethod: PaymentMethod;
  reference?: string;
  notes?: string;
  paidAt?: string;
  performedBy?: string;
}

/** An invoice with its lines, payments, and what is still owed (negative = we owe them). */
export interface InvoiceDetailView {
  invoice: Invoice;
  lines: InvoiceLine[];
  payments: InvoicePaymentLine[];
  returnedAmount: string;
  balanceDue: string;
}

export interface ReturnPayload {
  items: { detailId: number; quantity: number }[];
  reason?: string;
  refundMethod?: PaymentMethod;
}

// ── Dashboard ────────────────────────────────────────────────────────────────
export interface DashboardData {
  period: "week" | "month" | "quarter" | "year";
  days: number;
  shopId?: number | null;
  orders: { completed: number; inProgress: number; returns: number; overdue: number; total: number };
  stock: { inStock: number; lowStock: number; outOfStock: number; deadStock: number; products: number };
  flow: { date: string; in: number; out: number }[];
  attention: { productId: number; productName: string; sku?: string; reason: "OUT" | "LOW" | "EXPIRED" | "EXPIRING"; detail: string }[];
  attentionTotal: number;
  recentOrders: {
    id: number; invoiceNumber: string; type: InvoiceType;
    status: "SOLD" | "RECEIVED" | "RETURNED" | "PART_RETURNED" | "VOID";
    paymentStatus?: PaymentStatus; headline: string; itemCount: number; party: string;
    orderedBy?: string; total: string | number; paymentMethod?: PaymentMethod; createdAt?: string; shopName?: string;
  }[];
  products: {
    productId: number; productName: string; sku?: string; category?: string; supplier?: string; unit?: string;
    /** null for roles that may not see costs */
    cost: number | null; onHand: number; onHandValue: number | null; soldInPeriod: number;
    stockStatus: "IN" | "LOW" | "OUT" | "DEAD"; demand: "HIGH" | "NORMAL" | "LOW";
  }[];
}

// ── Super admin & team ──────────────────────────────────────────────────────
export interface CompanyAdmin {
  membershipId: number; userId: number; name: string; email: string; phone?: string;
  role: UserRole; pendingFirstSignIn: boolean;
}

export interface Company {
  id: number; name: string; code: string; email?: string; contact?: string;
  businessType?: string; businessTypeLabel?: string; suspended: boolean; createdAt?: string;
  admins: CompanyAdmin[]; locations: number; members: number; products: number; lastActivity?: string;
}

export interface NewPerson { firstName: string; lastName: string; email: string; phone?: string }

export interface CreateCompanyPayload {
  name: string; email?: string; contact?: string; businessType?: string; admin: NewPerson;
}

/** A sign-in to hand over. temporaryPassword is null when the person already had an account. */
export interface HandoverCredentials { name: string; email: string; temporaryPassword: string | null; existingAccount: boolean }

export interface CompanyResult { company: Company; credentials: HandoverCredentials }

export interface TeamMember {
  id: number; userId: number; username?: string; name: string; email: string; phone?: string; role: UserRole;
  locations: { id: number; name: string }[]; pendingFirstSignIn: boolean; you: boolean; canManage: boolean;
  /** Only owners and admins remove people; only owners remove other owners. */
  canRemove: boolean;
}

export interface TeamList { members: TeamMember[]; rolesYouCanAssign: UserRole[]; invitations: SentInvitation[] }

/** Invite someone who already has an account. */
export interface InvitePayload { username: string; role: UserRole; shopIds: number[] }

/** An open invitation, as the company sees it. */
export interface SentInvitation {
  id: number; userId: number; username: string; name: string; role: UserRole;
  locations: { id: number; name: string }[]; invitedBy: string; sentAt: string; expiresAt: string; canManage: boolean;
}

/** An open invitation, as the invited person sees it. */
export interface MyInvitation {
  id: number; businessId: number; businessName: string; businessLogo?: string; role: UserRole;
  locations: string[]; invitedBy: string; sentAt: string; expiresAt: string;
}

export interface NewTeamMember extends NewPerson { role: UserRole; shopIds: number[] }

/**
 * A new person: member + temporary password. Someone who already had an account: no member yet,
 * an invitation they must accept instead.
 */
export interface TeamAdded {
  member: TeamMember | null; temporaryPassword: string | null; existingAccount: boolean; invitation?: SentInvitation | null;
}

/** A company's name and contact details (Settings → Business info). */
export interface BusinessProfile {
  id: number; name: string; code: string; email?: string | null; contact?: string | null;
  secondaryContact?: string | null; logo?: string | null;
}
