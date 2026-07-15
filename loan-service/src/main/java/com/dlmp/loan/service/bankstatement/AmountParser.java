package com.dlmp.loan.service.bankstatement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Strips currency symbols/thousands-separators from bank-export amount strings. */
final class AmountParser {

    private AmountParser() {}

    static BigDecimal tryParse(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim()
                .replace("₹", "")
                .replace(",", "")
                .replace("Rs.", "")
                .replace("Rs", "")
                .trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equalsIgnoreCase("nil")) return null;
        boolean negative = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (negative) cleaned = cleaned.substring(1, cleaned.length() - 1);
        try {
            BigDecimal value = new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
            return negative ? value.negate() : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
