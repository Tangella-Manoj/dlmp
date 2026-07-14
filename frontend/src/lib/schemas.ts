import { z } from "zod";
import { LOAN_TYPES } from "@/types/domain";
import { LOAN_TYPE_BOUNDS } from "@/lib/loanMeta";

export const loginSchema = z.object({
  email: z.string().min(1, "Email is required").email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
});
export type LoginFormValues = z.infer<typeof loginSchema>;

// Mirrors user-service RegisterRequest validation exactly.
export const registerSchema = z.object({
  firstName: z.string().min(2, "At least 2 characters").max(50),
  lastName: z.string().min(2, "At least 2 characters").max(50),
  email: z.string().min(1, "Email is required").email("Enter a valid email"),
  password: z
    .string()
    .min(8, "At least 8 characters")
    .max(100)
    .regex(
      /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/,
      "Must contain uppercase, lowercase, a digit, and a special character (@$!%*?&)",
    ),
  phoneNumber: z
    .string()
    .regex(/^[6-9]\d{9}$/, "Enter a valid 10-digit Indian phone number")
    .optional()
    .or(z.literal("")),
  panNumber: z
    .string()
    .regex(/^[A-Z]{5}[0-9]{4}[A-Z]$/, "Enter a valid PAN number")
    .optional()
    .or(z.literal("")),
  monthlyIncome: z.coerce.number().min(0, "Cannot be negative").optional(),
});
export type RegisterFormInput = z.input<typeof registerSchema>;
export type RegisterFormValues = z.output<typeof registerSchema>;

// Mirrors loan-service LoanApplicationRequest validation, including the
// per-loan-type amount/tenure bounds LoanCommandService enforces server-side
// (LOAN_TYPE_BOUNDS) — catching an out-of-range value here instead of
// round-tripping to the backend for the same rejection.
export const loanApplicationSchema = z
  .object({
    loanType: z.enum(LOAN_TYPES),
    principalAmount: z.coerce
      .number()
      .min(1000, "Minimum amount is ₹1,000")
      .max(100_000_000, "Amount is too large"),
    tenureMonths: z.coerce.number().int().min(1).max(360),
    purpose: z.string().max(500).optional().or(z.literal("")),
    monthlyIncome: z.coerce.number().min(1, "Monthly income is required"),
    existingDebts: z.coerce.number().min(0).optional(),
  })
  .superRefine((data, ctx) => {
    const bounds = LOAN_TYPE_BOUNDS[data.loanType];
    if (data.principalAmount < bounds.min || data.principalAmount > bounds.max) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["principalAmount"],
        message: `${data.loanType} loans must be ₹${bounds.min.toLocaleString("en-IN")} – ₹${bounds.max.toLocaleString("en-IN")}`,
      });
    }
    if (data.tenureMonths > bounds.maxTenure) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["tenureMonths"],
        message: `Max tenure for ${data.loanType} is ${bounds.maxTenure} months`,
      });
    }
  });
export type LoanApplicationFormInput = z.input<typeof loanApplicationSchema>;
export type LoanApplicationFormValues = z.output<typeof loanApplicationSchema>;

export const loanDecisionSchema = z.object({
  remarks: z.string().max(1000).optional().or(z.literal("")),
  rejectionReason: z.string().max(1000).optional().or(z.literal("")),
});
export type LoanDecisionFormValues = z.infer<typeof loanDecisionSchema>;

// Mirrors payment-service PaymentRequest validation.
export const paymentSchema = z.object({
  amount: z.coerce.number().min(1, "Minimum payment is ₹1"),
  paymentType: z.enum(["EMI", "PREPAYMENT", "PENALTY"]),
  paymentMode: z.enum(["UPI", "NEFT", "RTGS", "IMPS"]),
  remarks: z.string().max(500).optional().or(z.literal("")),
});
export type PaymentFormInput = z.input<typeof paymentSchema>;
export type PaymentFormValues = z.output<typeof paymentSchema>;
