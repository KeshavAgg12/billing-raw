package com.shop.billing;

import com.shop.billing.Models.Summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Totals are worked out from purchases and invoices for the dates asked, not stored. */
public final class ReportService {
    private ReportService() {}

    public static Summary summary(String from, String to) {
        LocalDate fromDate, toDate;
        try {
            fromDate = LocalDate.parse(from);
            toDate = LocalDate.parse(to);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.bad("Send both dates like 2026-11-25.");
        }
        if (fromDate.isAfter(toDate)) throw ApiException.bad("The start date is after the end date.");

        return Db.run(c -> {
            BigDecimal purchase = Db.one(c,
                    "SELECT COALESCE(SUM(quantity * cp), 0) FROM purchase WHERE purchase_date BETWEEN ? AND ?",
                    rs -> rs.getBigDecimal(1), fromDate, toDate);
            BigDecimal sales = Db.one(c,
                    "SELECT COALESCE(SUM(total), 0) FROM invoice WHERE invoice_date BETWEEN ? AND ?",
                    rs -> rs.getBigDecimal(1), fromDate, toDate);
            BigDecimal cogs = Db.one(c,
                    "SELECT COALESCE(SUM(ii.pieces * ii.cp), 0) FROM invoice_item ii " +
                    "JOIN invoice i ON i.id = ii.invoice_id WHERE i.invoice_date BETWEEN ? AND ?",
                    rs -> rs.getBigDecimal(1), fromDate, toDate);
            return new Summary(fromDate.toString(), toDate.toString(), purchase, sales, cogs, sales.subtract(cogs));
        });
    }
}
