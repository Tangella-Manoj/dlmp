import { useState } from "react";
import { Dialog } from "@/components/ui/Dialog";
import { Textarea } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

export function RejectLoanDialog({
  open,
  onClose,
  onConfirm,
  loading,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
  loading?: boolean;
}) {
  const [reason, setReason] = useState("");

  return (
    <Dialog open={open} onClose={onClose} title="Reject Loan Application">
      <Textarea
        label="Rejection reason"
        placeholder="e.g. Debt-to-income ratio exceeds policy threshold"
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
      <div className="mt-6 flex gap-3">
        <Button variant="outline" className="flex-1" onClick={onClose}>
          Cancel
        </Button>
        <Button
          variant="danger"
          className="flex-1"
          disabled={!reason.trim()}
          loading={loading}
          onClick={() => onConfirm(reason)}
        >
          Reject
        </Button>
      </div>
    </Dialog>
  );
}
