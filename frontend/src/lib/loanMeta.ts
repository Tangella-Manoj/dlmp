import type { LoanStatus, LoanType } from "@/types/domain";

export const LOAN_TYPE_LABELS: Record<LoanType, string> = {
  PERSONAL: "Personal Loan",
  HOME: "Home Loan",
  VEHICLE: "Vehicle Loan",
  BUSINESS: "Business Loan",
  EDUCATION: "Education Loan",
  GOLD: "Gold Loan",
};

export const LOAN_TYPE_BOUNDS: Record<
  LoanType,
  { rate: number; maxTenure: number; min: number; max: number }
> = {
  PERSONAL: { rate: 14.0, maxTenure: 60, min: 10_000, max: 5_000_000 },
  HOME: { rate: 8.75, maxTenure: 300, min: 500_000, max: 50_000_000 },
  VEHICLE: { rate: 10.5, maxTenure: 84, min: 100_000, max: 10_000_000 },
  BUSINESS: { rate: 15.0, maxTenure: 120, min: 50_000, max: 25_000_000 },
  EDUCATION: { rate: 9.5, maxTenure: 84, min: 50_000, max: 20_000_000 },
  GOLD: { rate: 8.5, maxTenure: 36, min: 10_000, max: 5_000_000 },
};

type StatusTone = "slate" | "amber" | "blue" | "green" | "red" | "violet";

export const LOAN_STATUS_META: Record<LoanStatus, { label: string; tone: StatusTone }> = {
  DRAFT: { label: "Draft", tone: "slate" },
  PENDING_REVIEW: { label: "Pending Review", tone: "amber" },
  UNDER_REVIEW: { label: "Under Review", tone: "amber" },
  APPROVED: { label: "Approved", tone: "blue" },
  REJECTED: { label: "Rejected", tone: "red" },
  ACTIVE: { label: "Active", tone: "green" },
  CLOSED: { label: "Closed", tone: "violet" },
  DEFAULTED: { label: "Defaulted", tone: "red" },
  CANCELLED: { label: "Cancelled", tone: "slate" },
  NPA: { label: "NPA", tone: "red" },
};

export const STATUS_TONE_CLASSES: Record<StatusTone, string> = {
  slate: "bg-ink-100 text-ink-600 ring-ink-200",
  amber: "bg-warning-50 text-warning-700 ring-warning-500/20",
  blue: "bg-info-50 text-info-700 ring-info-500/20",
  green: "bg-success-50 text-success-700 ring-success-500/20",
  red: "bg-danger-50 text-danger-700 ring-danger-500/20",
  violet: "bg-brand-50 text-brand-700 ring-brand-500/20",
};
