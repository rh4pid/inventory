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

export interface Shop {
  id: number;
  shopCode: string;
  shopName: string;
  shopLocation?: string;
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

export interface StockInPayload {
  productId: number;
  shopId: number;
  quantity: number;
  notes?: string;
  supplierId?: number;
  costPrice?: number;
}

export interface StockOutPayload {
  productId: number;
  shopId: number;
  quantity: number;
  notes?: string;
  reason?: string;
}

export interface StockTransferPayload {
  productId: number;
  fromShopId: number;
  toShopId: number;
  quantity: number;
  notes?: string;
}

export interface StockAdjustmentPayload {
  productId: number;
  shopId: number;
  newQuantity: number;
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
  unitPrice?: number;
  discountAmount?: number;
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
  unitPrice: number;
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
