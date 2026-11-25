package com.shop.billing;

import com.shop.billing.Models.ArticleDto;
import com.shop.billing.Models.Availability;
import com.shop.billing.Models.CustomerDto;
import com.shop.billing.Models.InvoiceItemReq;
import com.shop.billing.Models.InvoiceItemRes;
import com.shop.billing.Models.InvoiceReq;
import com.shop.billing.Models.InvoiceSummary;
import com.shop.billing.Models.InvoiceRes;
import com.shop.billing.Models.LooseAvail;
import com.shop.billing.Models.SetAvail;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class InvoiceService {
    private InvoiceService() {}

    /** One line of the invoice while it is being built. */
    private static final class Line {
        ArticleDto article;
        String type;          // SET or LOOSE
        Long setTypeId;
        String setName;
        Integer size;
        int quantity;         // sets for SET, pairs for LOOSE
        int pieces;           // total pairs on this line
        BigDecimal rate;
        BigDecimal lineTotal;
    }

    public static InvoiceRes create(InvoiceReq req) {
        if (req == null || req.customerId() == null) throw ApiException.bad("Choose a customer for this bill.");
        if (req.items() == null || req.items().isEmpty()) throw ApiException.bad("Add at least one item to the bill.");

        // Everything below runs in ONE transaction: if anything fails, stock is not touched.
        return Db.run(c -> {
            CustomerDto customer = CustomerService.find(c, req.customerId());
            if (customer == null) throw ApiException.notFound("Customer not found.");

            List<Line> lines = new ArrayList<>();
            List<String> messages = new ArrayList<>();
            int totalSetPieces = 0;

            // Pass 1: add what is in stock, take it out of stock, note what was short
            for (InvoiceItemReq r : req.items()) {
                if (r.articleId() == null) throw ApiException.bad("Every item needs an article.");
                if (r.quantity() <= 0) throw ApiException.bad("Quantity must be above zero.");

                ArticleDto a = ArticleService.get(c, r.articleId());
                if (a == null) throw ApiException.notFound("Article " + r.articleId() + " not found.");
                Availability av = ArticleService.availability(c, a);

                Line line = new Line();
                line.article = a;

                if ("SET".equals(r.type())) {
                    if (r.setTypeId() == null) throw ApiException.bad("Choose a set for a SET item.");
                    SetAvail sa = null;
                    for (SetAvail s : av.sets()) {
                        if (s.setTypeId() == r.setTypeId()) sa = s;
                    }
                    if (sa == null) throw ApiException.bad("That set is not defined for " + a.name() + ".");

                    int added = Math.min(r.quantity(), sa.available());
                    if (added < r.quantity()) {
                        messages.add(a.name() + " set " + sa.setName() + ": asked " + r.quantity() + ", added "
                                + added + (added == 0 ? " (no full set in stock)" : "")
                                + ". Loose available: " + looseText(av));
                    }
                    if (added == 0) continue;

                    for (int size : sa.sizes()) deduct(c, a.id(), size, added);
                    line.type = "SET";
                    line.setTypeId = sa.setTypeId();
                    line.setName = sa.setName();
                    line.quantity = added;
                    line.pieces = added * sa.sizes().size();
                    totalSetPieces += line.pieces;

                } else if ("LOOSE".equals(r.type())) {
                    if (r.size() == null) throw ApiException.bad("Choose a size for a LOOSE item.");
                    int looseAvailable = 0;
                    for (LooseAvail l : av.loose()) {
                        if (l.size() == r.size()) looseAvailable = l.quantity();
                    }

                    int added = Math.min(r.quantity(), looseAvailable);
                    if (added < r.quantity()) {
                        messages.add(a.name() + " loose size " + r.size() + ": asked " + r.quantity() + ", added "
                                + added + ". Loose available: " + looseText(av));
                    }
                    if (added == 0) continue;

                    deduct(c, a.id(), r.size(), added);
                    line.type = "LOOSE";
                    line.size = r.size();
                    line.quantity = added;
                    line.pieces = added;

                } else {
                    throw ApiException.bad("Item type must be SET or LOOSE.");
                }
                lines.add(line);
            }

            if (lines.isEmpty()) {
                throw ApiException.conflict("Nothing could be added to the bill. " + String.join(" | ", messages));
            }

            // Pass 2: rates. The 32-pair rule needs the set pairs of the WHOLE invoice, so it comes after pass 1.
            BigDecimal total = BigDecimal.ZERO;
            for (Line line : lines) {
                BigDecimal sp = line.article.sp();
                if (line.type.equals("SET")) {
                    line.rate = totalSetPieces > Config.bulkThresholdPieces() ? sp.subtract(Config.bulkDiscount()) : sp;
                } else {
                    line.rate = sp.add(Config.looseExtra());
                }
                line.lineTotal = line.rate.multiply(BigDecimal.valueOf(line.pieces));
                total = total.add(line.lineTotal);
            }

            LocalDate today = LocalDate.now();
            long invoiceId = Db.insert(c, "INSERT INTO invoice (customer_id, invoice_date, total) VALUES (?, ?, ?)",
                    customer.id(), today, total);

            List<InvoiceItemRes> items = new ArrayList<>();
            for (Line line : lines) {
                // cp is saved with the line, so old profits stay right even if cp changes later
                Db.update(c, "INSERT INTO invoice_item (invoice_id, article_id, type, set_type_id, size, quantity, "
                        + "pieces, rate, cp, line_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        invoiceId, line.article.id(), line.type, line.setTypeId, line.size, line.quantity,
                        line.pieces, line.rate, line.article.cp(), line.lineTotal);
                items.add(new InvoiceItemRes(line.article.id(), line.article.name(), line.type, line.setName,
                        line.size, line.quantity, line.pieces, line.rate, line.lineTotal));
            }
            return new InvoiceRes(invoiceId, customer.id(), customer.name(), today.toString(), total, items, messages);
        });
    }

    /** Invoices of customers whose name contains the text (empty text = latest invoices). */
    public static List<InvoiceSummary> search(String customerName) {
        String text = customerName == null ? "" : customerName.trim().toLowerCase();
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return Db.run(c -> Db.query(c,
                "SELECT i.id, i.customer_id, cu.name, i.invoice_date, i.total FROM invoice i "
                + "JOIN customer cu ON cu.id = i.customer_id "
                + "WHERE LOWER(cu.name) LIKE ? ESCAPE '\\' ORDER BY i.id DESC LIMIT 50",
                rs -> new InvoiceSummary(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getDate(4).toString(), rs.getBigDecimal(5)),
                pattern));
    }

    public static InvoiceRes findById(long id) {
        return Db.run(c -> {
            Object[] head = Db.one(c,
                    "SELECT i.id, i.customer_id, cu.name, i.invoice_date, i.total FROM invoice i "
                    + "JOIN customer cu ON cu.id = i.customer_id WHERE i.id = ?",
                    rs -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getString(3),
                            rs.getDate(4).toString(), rs.getBigDecimal(5)}, id);
            if (head == null) throw ApiException.notFound("Invoice " + id + " not found.");

            List<InvoiceItemRes> items = Db.query(c,
                    "SELECT ii.article_id, a.name AS article_name, ii.type, st.name AS set_name, ii.size, "
                    + "ii.quantity, ii.pieces, ii.rate, ii.line_total FROM invoice_item ii "
                    + "JOIN article a ON a.id = ii.article_id "
                    + "LEFT JOIN set_type st ON st.id = ii.set_type_id "
                    + "WHERE ii.invoice_id = ? ORDER BY ii.id",
                    InvoiceService::mapItem, id);

            return new InvoiceRes((Long) head[0], (Long) head[1], (String) head[2], (String) head[3],
                    (BigDecimal) head[4], items, List.of());
        });
    }

    private static InvoiceItemRes mapItem(ResultSet rs) throws SQLException {
        int size = rs.getInt("size");
        Integer sizeOrNull = rs.wasNull() ? null : size;
        return new InvoiceItemRes(rs.getLong("article_id"), rs.getString("article_name"), rs.getString("type"),
                rs.getString("set_name"), sizeOrNull, rs.getInt("quantity"), rs.getInt("pieces"),
                rs.getBigDecimal("rate"), rs.getBigDecimal("line_total"));
    }

    private static void deduct(Connection c, long articleId, int size, int quantity) throws SQLException {
        int changed = Db.update(c,
                "UPDATE stock SET quantity = quantity - ? WHERE article_id = ? AND size = ? AND quantity >= ?",
                quantity, articleId, size, quantity);
        if (changed == 0) throw ApiException.conflict("Not enough stock for size " + size + ".");
    }

    private static String looseText(Availability av) {
        if (av.loose().isEmpty()) return "none";
        return av.loose().stream().map(l -> l.size() + ":" + l.quantity()).collect(Collectors.joining(", "));
    }
}
