import type { InputHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

interface SliderProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "type"> {
  value: number;
  min: number;
  max: number;
  step?: number;
  onValueChange: (value: number) => void;
}

/** Native range input, restyled to match the design system — the accent color
 * fills the track up to the thumb so the slider visually communicates
 * progress toward the max, not just a bare handle on a flat line. */
export function Slider({ value, min, max, step = 1, onValueChange, className, ...props }: SliderProps) {
  const pct = max > min ? ((value - min) / (max - min)) * 100 : 0;
  return (
    <input
      type="range"
      min={min}
      max={max}
      step={step}
      value={value}
      onChange={(e) => onValueChange(Number(e.target.value))}
      className={cn(
        "h-2 w-full cursor-pointer appearance-none rounded-full bg-ink-100",
        "[&::-webkit-slider-thumb]:size-5 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full",
        "[&::-webkit-slider-thumb]:bg-brand-600 [&::-webkit-slider-thumb]:shadow-md [&::-webkit-slider-thumb]:ring-4 [&::-webkit-slider-thumb]:ring-brand-100",
        "[&::-moz-range-thumb]:size-5 [&::-moz-range-thumb]:appearance-none [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:border-0",
        "[&::-moz-range-thumb]:bg-brand-600 [&::-moz-range-thumb]:shadow-md [&::-moz-range-thumb]:ring-4 [&::-moz-range-thumb]:ring-brand-100",
        className,
      )}
      style={{ background: `linear-gradient(to right, #4f46e5 ${pct}%, #eef0f4 ${pct}%)` }}
      {...props}
    />
  );
}
