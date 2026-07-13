import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/context/AuthContext";
import { registerSchema, type RegisterFormInput, type RegisterFormValues } from "@/lib/schemas";

export function RegisterPage() {
  const { register: registerUser } = useAuth();
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormInput, unknown, RegisterFormValues>({
    resolver: zodResolver(registerSchema),
  });

  async function onSubmit(values: RegisterFormValues) {
    setSubmitting(true);
    try {
      await registerUser({
        ...values,
        phoneNumber: values.phoneNumber || undefined,
        panNumber: values.panNumber || undefined,
      });
      toast.success("Account created — welcome to DLMP!");
      navigate("/", { replace: true });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Start your loan application in minutes">
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
          {...register("email")}
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
