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
}

export interface LoginPayload { email: string; password: string; }
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
  | "CASH" | "BANK" | "ACCOUNTS_RECEIVABLE" | "INVENTORY"
  | "ACCOUNTS_PAYABLE" | "SALES_REVENUE" | "COST_OF_GOODS_SOLD"
  | "PURCHASE" | "DISCOUNT_ALLOWED" | "TAX_PAYABLE" | "OTHER";

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
