// ─── Roles ──────────────────────────────────────────────────────────────────
export type UserRole = "ROLE_CUSTOMER" | "ROLE_LOAN_OFFICER" | "ROLE_ADMIN";

// ─── Auth ───────────────────────────────────────────────────────────────────
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  accessExpiresInMs: number;
  userId: string;
  email: string;
  firstName: string;
  role: UserRole;
}

export interface UserResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber?: string;
  panNumber?: string;
  role: UserRole;
  status: string;
  monthlyIncome?: number;
  createdAt: string;
  lastLogin?: string;
}

// ─── Loans ──────────────────────────────────────────────────────────────────
export const LOAN_TYPES = [
  "PERSONAL",
  "HOME",
  "VEHICLE",
  "BUSINESS",
  "EDUCATION",
  "GOLD",
] as const;
export type LoanType = (typeof LOAN_TYPES)[number];

export const LOAN_STATUSES = [
  "DRAFT",
  "PENDING_REVIEW",
  "UNDER_REVIEW",
  "APPROVED",
  "REJECTED",
  "ACTIVE",
  "CLOSED",
  "DEFAULTED",
  "CANCELLED",
  "NPA",
] as const;
export type LoanStatus = (typeof LOAN_STATUSES)[number];

export interface LoanResponse {
  id: string;
  loanNumber: string;
  userId: string;
  loanType: LoanType;
  principalAmount: number;
  sanctionedAmount?: number;
  outstandingPrincipal?: number;
  interestRate: number;
  tenureMonths: number;
  emiAmount?: number;
  totalInterestPayable?: number;
  totalAmountPayable?: number;
  processingFee?: number;
  status: LoanStatus;
  disbursementDate?: string;
  maturityDate?: string;
  firstEmiDate?: string;
  purpose?: string;
  rejectionReason?: string;
  creditScore?: number;
  riskCategory?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface EmiScheduleResponse {
  id: string;
  installmentNumber: number;
  dueDate: string;
  emiAmount: number;
  principalComponent: number;
  interestComponent: number;
  openingBalance: number;
  closingBalance: number;
  paidAmount: number;
  paidDate?: string;
  status: "PENDING" | "PARTIAL" | "PAID";
  penaltyAmount: number;
  totalDue: number;
  daysOverdue: number;
}

export interface LoanApplicationRequest {
  loanType: LoanType;
  principalAmount: number;
  tenureMonths: number;
  purpose?: string;
  monthlyIncome: number;
  existingDebts?: number;
  useVerifiedLimit?: boolean;
}

// ─── OTP consent & bank statement analysis ─────────────────────────────────
export type OtpPurpose = "LOAN_APPLICATION" | "LIMIT_INCREASE";

export interface BankStatementAnalysis {
  id: string;
  fileName?: string;
  sourceType: "CSV" | "PDF";
  status: "PENDING" | "COMPLETED" | "FAILED";
  failureReason?: string;
  periodStart?: string;
  periodEnd?: string;
  monthsCovered?: number;
  transactionCount?: number;
  verifiedMonthlyIncome?: number;
  avgMonthlyBalance?: number;
  avgMonthlyOutflow?: number;
  bounceCount?: number;
  verifiedEligibleAmount?: number;
  reconciliationConfidence?: number;
  createdAt: string;
}

export interface LoanDecisionRequest {
  remarks?: string;
  rejectionReason?: string;
}

// ─── Payments ───────────────────────────────────────────────────────────────
export interface PaymentRequest {
  loanId: string;
  amount: number;
  principalAmount?: number;
  interestAmount?: number;
  penaltyAmount?: number;
  paymentType: "EMI" | "PREPAYMENT" | "PENALTY";
  paymentMode?: "UPI" | "NEFT" | "RTGS" | "IMPS";
  remarks?: string;
}

export interface PaymentResponse {
  id: string;
  paymentReference: string;
  loanId: string;
  userId: string;
  amount: number;
  principalComponent: number;
  interestComponent: number;
  penaltyComponent: number;
  paymentType: string;
  paymentMode?: string;
  paymentDate: string;
  status: string;
  remarks?: string;
  createdAt: string;
  idempotent: boolean;
}

// ─── Notifications ──────────────────────────────────────────────────────────
export interface Notification {
  id: string;
  userId: string;
  title: string;
  message: string;
  notificationType?: "LOAN" | "PAYMENT" | "SECURITY" | "SYSTEM";
  read: boolean;
  readAt?: string;
  createdAt: string;
}

// ─── Reports ────────────────────────────────────────────────────────────────
export interface PortfolioStats {
  totalLoans: number;
  activeLoans: number;
  pendingLoans?: number;
  pendingReview?: number;
  totalDisbursed: number;
  totalRecovered?: number;
  outstandingPrincipal?: number;
}

export interface LoanStatSnapshot {
  id: string;
  loanId: string;
  loanNumber: string;
  userId: string;
  loanType: string;
  principalAmount: number;
  disbursedAmount?: number;
  totalPaidAmount: number;
  emiAmount?: number;
  currentStatus: string;
  creditScore?: number;
  riskCategory?: string;
  tenureMonths?: number;
  applicationDate?: string;
  disbursementDate?: string;
  lastPaymentDate?: string;
  lastPaymentAmount?: number;
  paymentCount: number;
  rejectionReason?: string;
  updatedAt?: string;
}
