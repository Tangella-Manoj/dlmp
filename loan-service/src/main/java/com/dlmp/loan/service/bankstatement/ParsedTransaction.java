package com.dlmp.loan.service.bankstatement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row extracted from an uploaded statement. Balance is optional — not every export includes a running balance per line. */
public record ParsedTransaction(
        LocalDate date,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance
) {
    public ParsedTransaction {
        debit = debit == null ? BigDecimal.ZERO : debit;
        credit = credit == null ? BigDecimal.ZERO : credit;
    }
}
