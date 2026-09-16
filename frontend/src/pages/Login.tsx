import { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useLocation, useNavigate, useSearchParams, type Location } from "react-router-dom";
import toast from "react-hot-toast";
import { AlertCircle } from "lucide-react";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/context/AuthContext";
import { loginSchema, type LoginFormValues } from "@/lib/schemas";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const queryEmail = searchParams.get("email") ?? "";

  const [submitting, setSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: queryEmail, password: "" },
  });

  useEffect(() => {
    if (queryEmail) {
      setValue("email", queryEmail);
    }
  }, [queryEmail, setValue]);

  async function onSubmit(values: LoginFormValues) {
    setSubmitting(true);
    setLoginError(null);
    clearErrors();
    try {
      await login(values);
      const from = (location.state as { from?: Location })?.from?.pathname ?? "/";
      navigate(from, { replace: true });
      toast.success("Welcome back!");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Login failed";
      setLoginError(message);
      toast.error(message);

      const lower = message.toLowerCase();
      if (lower.includes("email") || lower.includes("account found")) {
        setError("email", { type: "server", message });
      } else if (lower.includes("password")) {
        setError("password", { type: "server", message });
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handleInputChange() {
    if (loginError) setLoginError(null);
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to manage your loans and payments">
      {loginError && (
        <div
          role="alert"
          className="mb-4 flex items-start gap-2.5 rounded-xl border border-danger-200 bg-danger-50 p-3.5 text-sm text-danger-800"
        >
          <AlertCircle className="mt-0.5 size-4 shrink-0 text-danger-600" />
          <div className="flex-1 font-medium leading-snug">{loginError}</div>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
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
          autoComplete="current-password"
          placeholder="••••••••"
          error={errors.password?.message}
          {...register("password", { onChange: handleInputChange })}
        />
        <Button type="submit" className="w-full" size="lg" loading={submitting}>
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-500">
        Don't have an account?{" "}
        <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700">
          Create one
        </Link>
      </p>
    </AuthLayout>
  );
}
