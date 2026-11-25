package com.shop.billing;

import com.shop.billing.Models.ArticleDto;
import com.shop.billing.Models.PurchaseItemReq;
import com.shop.billing.Models.PurchaseReq;
import com.shop.billing.Models.PurchaseRes;
import com.shop.billing.Models.SetTypeDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

/** Stock coming in: adds to the size-wise stock and keeps a purchase row for the profit report. */
public final class StockService {
    private StockService() {}

    public static PurchaseRes addStock(PurchaseReq r) {
        if (r == null || r.articleId() == null) throw ApiException.bad("Choose an article.");
        if (r.cp() == null || r.cp().signum() <= 0) throw ApiException.bad("Cost price must be above zero.");
        if (r.items() == null || r.items().isEmpty()) throw ApiException.bad("Enter pairs for at least one size.");

        LocalDate date;
        try {
            date = r.purchaseDate() == null || r.purchaseDate().isBlank()
                    ? LocalDate.now() : LocalDate.parse(r.purchaseDate());
        } catch (DateTimeParseException e) {
            throw ApiException.bad("Date must look like 2026-11-25.");
        }
        final LocalDate purchaseDate = date;

        return Db.run(c -> {
            ArticleDto a = ArticleService.get(c, r.articleId());
            if (a == null) throw ApiException.notFound("Article " + r.articleId() + " not found.");

            Set<Integer> allowed = new HashSet<>();
            for (SetTypeDto s : a.setTypes()) allowed.addAll(s.sizes());

            Set<Integer> seen = new HashSet<>();
            int totalPieces = 0;
            long firstId = 0;

            for (PurchaseItemReq it : r.items()) {
                if (it.size() <= 0 || it.quantity() <= 0) throw ApiException.bad("Size and pairs must be above zero.");
                if (!allowed.contains(it.size())) {
                    throw ApiException.bad("Size " + it.size() + " is not part of this article's sets.");
                }
                if (!seen.add(it.size())) throw ApiException.bad("Size " + it.size() + " is repeated.");

                int changed = Db.update(c, "UPDATE stock SET quantity = quantity + ? WHERE article_id = ? AND size = ?",
                        it.quantity(), a.id(), it.size());
                if (changed == 0) {
                    Db.update(c, "INSERT INTO stock (article_id, size, quantity) VALUES (?, ?, ?)",
                            a.id(), it.size(), it.quantity());
                }
                long pid = Db.insert(c,
                        "INSERT INTO purchase (article_id, size, quantity, cp, purchase_date) VALUES (?, ?, ?, ?, ?)",
                        a.id(), it.size(), it.quantity(), r.cp(), purchaseDate);
                if (firstId == 0) firstId = pid;
                totalPieces += it.quantity();
            }

            // the article keeps the latest cost price
            Db.update(c, "UPDATE article SET cp = ? WHERE id = ?", r.cp(), a.id());

            return new PurchaseRes(firstId, a.id(), a.name(), r.cp(), purchaseDate.toString(), totalPieces,
                    r.cp().multiply(BigDecimal.valueOf(totalPieces)));
        });
    }
}
