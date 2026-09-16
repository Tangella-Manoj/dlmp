import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { AlertCircle, ArrowRight } from "lucide-react";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/context/AuthContext";
import { registerSchema, type RegisterFormInput, type RegisterFormValues } from "@/lib/schemas";

export function RegisterPage() {
  const { register: registerUser } = useAuth();
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [duplicateEmail, setDuplicateEmail] = useState<string | null>(null);
  const [registerError, setRegisterError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<RegisterFormInput, unknown, RegisterFormValues>({
    resolver: zodResolver(registerSchema),
  });

  async function onSubmit(values: RegisterFormValues) {
    setSubmitting(true);
    setDuplicateEmail(null);
    setRegisterError(null);
    clearErrors();
    try {
      await registerUser({
        ...values,
        phoneNumber: values.phoneNumber || undefined,
        panNumber: values.panNumber || undefined,
      });
      toast.success("Account created — welcome to DLMP!");
      navigate("/", { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Registration failed";
      const isDuplicate =
        message.toLowerCase().includes("already registered") ||
        message.toLowerCase().includes("already exists");

      if (isDuplicate) {
        setDuplicateEmail(values.email);
        setError("email", { type: "server", message: "An account with this email already exists." });
        toast.error("An account with this email already exists.");
      } else {
        setRegisterError(message);
        toast.error(message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handleInputChange() {
    if (duplicateEmail) setDuplicateEmail(null);
    if (registerError) setRegisterError(null);
  }

  return (
    <AuthLayout title="Create your account" subtitle="Start your loan application in minutes">
      {duplicateEmail && (
        <div
          role="alert"
          className="mb-4 rounded-xl border border-amber-300 bg-amber-50 p-4 text-xs sm:text-sm text-amber-950"
        >
          <div className="flex items-start gap-2.5">
            <AlertCircle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="font-bold text-ink-900 text-sm">Account Already Exists</p>
              <p className="mt-1 text-ink-700">
                An account with <strong className="font-semibold text-ink-900">{duplicateEmail}</strong> already exists.
              </p>
              <div className="mt-3 pt-2 border-t border-amber-200/80">
                <Link
                  to={`/login?email=${encodeURIComponent(duplicateEmail)}`}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-brand-600 text-white hover:bg-brand-700 shadow-xs transition-colors"
                >
                  <span>Sign In with this email</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </Link>
              </div>
            </div>
          </div>
        </div>
      )}

      {registerError && (
        <div
          role="alert"
          className="mb-4 flex items-start gap-2.5 rounded-xl border border-danger-200 bg-danger-50 p-3.5 text-sm text-danger-800"
        >
          <AlertCircle className="mt-0.5 size-4 shrink-0 text-danger-600" />
          <div className="flex-1 font-medium leading-snug">{registerError}</div>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <div className="grid grid-cols-2 gap-3">
          <Input label="First name" placeholder="Jane" error={errors.firstName?.message} {...register("firstName")} />
          <Input label="Last name" placeholder="Doe" error={errors.lastName?.message} {...register("lastName")} />
        </div>
        <Input
          label="Email address"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          error={errors.email?.message}
          {...register("email", { onChange: handleInputChange })}
        />
        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          placeholder="••••••••"
          hint="8+ characters, mixing case, a digit, and a special character"
          error={errors.password?.message}
          {...register("password")}
        />
        <div className="grid grid-cols-2 gap-3">
          <Input
            label="Phone (optional)"
            placeholder="9876543210"
            error={errors.phoneNumber?.message}
            {...register("phoneNumber")}
          />
          <Input
            label="Monthly income"
            type="number"
            placeholder="75000"
            error={errors.monthlyIncome?.message}
            {...register("monthlyIncome")}
          />
        </div>
        <Input
          label="PAN (optional)"
          placeholder="ABCDE1234F"
          className="uppercase"
          error={errors.panNumber?.message}
          {...register("panNumber")}
        />
        <Button type="submit" className="w-full" size="lg" loading={submitting}>
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-500">
        Already have an account?{" "}
        <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  );
}
