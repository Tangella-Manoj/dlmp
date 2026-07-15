import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { Dialog } from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { otpApi } from "@/api/otp";
import { apiErrorMessage } from "@/api/client";
import type { OtpPurpose } from "@/types/domain";

const RESEND_COOLDOWN_S = 30;

const PURPOSE_COPY: Record<OtpPurpose, { title: string; body: string }> = {
  LOAN_APPLICATION: {
    title: "Confirm it's you",
    body: "We'll email a 6-digit code to confirm your consent before submitting a loan application.",
  },
  LIMIT_INCREASE: {
    title: "Confirm your limit increase request",
    body: "We'll email a 6-digit code to confirm your consent to use a verified, income-backed credit limit.",
  },
};

/**
 * Real, free email OTP (no SMS gateway has a free-forever tier). On success,
 * the backend grants a 30-minute consent window for {@code purpose} — this
 * dialog doesn't track that window itself, the gated action's own request
 * will simply succeed or fail based on it.
 */
export function OtpVerifyDialog({
  open,
  onClose,
  purpose,
  onVerified,
}: {
  open: boolean;
  onClose: () => void;
  purpose: OtpPurpose;
  onVerified: () => void;
}) {
  const [code, setCode] = useState("");
  const [sent, setSent] = useState(false);
  const [sending, setSending] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (!open) {
      setCode("");
      setSent(false);
      setCooldown(0);
    }
  }, [open]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  async function sendCode() {
    setSending(true);
    try {
      await otpApi.request(purpose);
      setSent(true);
      setCooldown(RESEND_COOLDOWN_S);
      toast.success("Code sent — check your email");
    } catch (err) {
      toast.error(apiErrorMessage(err));
    } finally {
      setSending(false);
    }
  }

  async function verify() {
    if (code.length !== 6) return;
    setVerifying(true);
    try {
      await otpApi.verify(purpose, code);
      toast.success("Verified");
      onVerified();
      onClose();
    } catch (err) {
      toast.error(apiErrorMessage(err));
    } finally {
      setVerifying(false);
    }
  }

  const copy = PURPOSE_COPY[purpose];

  return (
    <Dialog open={open} onClose={onClose} title={copy.title}>
      <div className="space-y-4">
        <p className="text-sm text-ink-500">{copy.body}</p>

        {!sent ? (
          <Button className="w-full" loading={sending} onClick={sendCode}>
            Send code to my email
          </Button>
        ) : (
          <>
            <Input
              label="6-digit code"
              inputMode="numeric"
              maxLength={6}
              placeholder="123456"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
              onKeyDown={(e) => e.key === "Enter" && verify()}
              autoFocus
            />
            <div className="flex gap-3">
              <Button
                type="button"
                variant="outline"
                className="flex-1"
                disabled={cooldown > 0}
                loading={sending}
                onClick={sendCode}
              >
                {cooldown > 0 ? `Resend in ${cooldown}s` : "Resend code"}
              </Button>
              <Button
                className="flex-1"
                disabled={code.length !== 6}
                loading={verifying}
                onClick={verify}
              >
                Verify
              </Button>
            </div>
          </>
        )}
      </div>
    </Dialog>
  );
}
