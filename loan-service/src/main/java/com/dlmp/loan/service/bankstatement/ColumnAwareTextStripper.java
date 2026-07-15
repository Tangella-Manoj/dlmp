package com.dlmp.loan.service.bankstatement;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A PDF bank statement is a table, but PDFBox's default text extraction just
 * concatenates characters left-to-right with whatever whitespace happens to
 * fall between them — inconsistent inter-word spacing frequently collapses
 * "Date  Description  1,500.00  8,500.00" into something a plain regex can't
 * reliably split back into columns, and a blank cell (common for whichever
 * of debit/credit doesn't apply to a row) draws no text at all, so there is
 * no whitespace run to split on in the first place.
 *
 * This subclass groups text runs into visual rows and records each run's
 * starting X position rather than discarding it. That lets the parser
 * align a row's cells against the header row's actual column positions
 * (the same way a human reads a table) instead of guessing which column a
 * value belongs to from its order among non-blank cells — the latter is
 * ambiguous exactly when a blank cell renders no text, which is common.
 */
class ColumnAwareTextStripper extends PDFTextStripper {

    private static final float GAP_THRESHOLD_FONT_MULTIPLIER = 1.4f;

    record Field(float x, String text) {
    }

    private final List<List<Field>> lines = new ArrayList<>();
    private List<Field> currentLine = new ArrayList<>();
    private StringBuilder currentField = new StringBuilder();
    private float currentFieldStartX = -1f;
    private float lastEndX = -1f;
    private float lastY = -1f;

    ColumnAwareTextStripper() throws IOException {
        super();
        setSortByPosition(true);
    }

    List<List<Field>> extractLines(PDDocument doc) throws IOException {
        lines.clear();
        currentLine = new ArrayList<>();
        currentField = new StringBuilder();
        currentFieldStartX = -1f;
        lastEndX = -1f;
        lastY = -1f;
        getText(doc);
        flushField();
        flushLine();
        return lines;
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (textPositions.isEmpty()) {
            return;
        }

        TextPosition first = textPositions.get(0);
        float startX = first.getXDirAdj();
        float y = first.getYDirAdj();
        float fontSize = Math.max(first.getFontSizeInPt(), 1f);

        boolean sameLine = lastY >= 0 && Math.abs(y - lastY) < fontSize * 0.5f;
        if (sameLine && lastEndX >= 0) {
            float gap = startX - lastEndX;
            if (gap > fontSize * GAP_THRESHOLD_FONT_MULTIPLIER) {
                flushField();
            }
        }
        if (currentFieldStartX < 0) {
            currentFieldStartX = startX;
        }
        currentField.append(text);

        TextPosition last = textPositions.get(textPositions.size() - 1);
        lastEndX = last.getXDirAdj() + last.getWidthDirAdj();
        lastY = y;
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        flushField();
        flushLine();
        lastEndX = -1f;
        lastY = -1f;
    }

    private void flushField() {
        if (!currentField.isEmpty()) {
            currentLine.add(new Field(currentFieldStartX, currentField.toString().trim()));
        }
        currentField = new StringBuilder();
        currentFieldStartX = -1f;
    }

    private void flushLine() {
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }
        currentLine = new ArrayList<>();
    }
}
