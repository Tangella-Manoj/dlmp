import {
  forwardRef,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";
import { cn } from "@/lib/utils";

interface FieldWrapperProps {
  label?: string;
  error?: string;
  hint?: string;
}

function FieldChrome({
  label,
  error,
  hint,
  htmlFor,
  children,
}: FieldWrapperProps & { htmlFor?: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      {label && (
        <label htmlFor={htmlFor} className="block text-sm font-medium text-ink-700">
          {label}
        </label>
      )}
      {children}
      {error ? (
        <p className="text-sm text-danger-600">{error}</p>
      ) : hint ? (
        <p className="text-sm text-ink-400">{hint}</p>
      ) : null}
    </div>
  );
}

const baseFieldClasses =
  "block w-full rounded-xl border-0 py-2.5 px-3.5 text-ink-900 ring-1 ring-inset ring-ink-300 " +
  "placeholder:text-ink-400 focus:ring-2 focus:ring-inset focus:ring-brand-600 " +
  "disabled:cursor-not-allowed disabled:bg-ink-50 disabled:text-ink-400 sm:text-sm transition-shadow";

type InputProps = InputHTMLAttributes<HTMLInputElement> & FieldWrapperProps;

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, hint, id, ...props }, ref) => (
    <FieldChrome label={label} error={error} hint={hint} htmlFor={id}>
      <input
        ref={ref}
        id={id}
        className={cn(baseFieldClasses, error && "ring-danger-400 focus:ring-danger-500", className)}
        {...props}
      />
    </FieldChrome>
  ),
);
Input.displayName = "Input";

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & FieldWrapperProps;

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, hint, id, ...props }, ref) => (
    <FieldChrome label={label} error={error} hint={hint} htmlFor={id}>
      <textarea
        ref={ref}
        id={id}
        className={cn(baseFieldClasses, "min-h-24 resize-y", error && "ring-danger-400 focus:ring-danger-500", className)}
        {...props}
      />
    </FieldChrome>
  ),
);
Textarea.displayName = "Textarea";

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & FieldWrapperProps;

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, label, error, hint, id, children, ...props }, ref) => (
    <FieldChrome label={label} error={error} hint={hint} htmlFor={id}>
      <select
        ref={ref}
        id={id}
        className={cn(baseFieldClasses, "bg-white pr-8", error && "ring-danger-400 focus:ring-danger-500", className)}
        {...props}
      >
        {children}
      </select>
    </FieldChrome>
  ),
);
Select.displayName = "Select";
