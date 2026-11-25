package com.shop.billing;

import com.shop.billing.Models.ArticleDto;
import com.shop.billing.Models.Availability;
import com.shop.billing.Models.CreateArticleReq;
import com.shop.billing.Models.LooseAvail;
import com.shop.billing.Models.SetAvail;
import com.shop.billing.Models.SetTypeDto;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ArticleService {
    private ArticleService() {}

    public static ArticleDto create(CreateArticleReq r) {
        if (r == null || r.name() == null || r.name().isBlank()) throw ApiException.bad("Article name is required.");
        if (r.cp() == null || r.sp() == null || r.cp().signum() <= 0 || r.sp().signum() <= 0) {
            throw ApiException.bad("Cost price and selling price must be above zero.");
        }
        if (r.setTypeIds() == null || r.setTypeIds().isEmpty()) {
            throw ApiException.bad("Pick at least one set for this article.");
        }
        Set<Long> setIds = new LinkedHashSet<>(r.setTypeIds());

        return Db.run(c -> {
            List<SetTypeDto> sets = new ArrayList<>();
            Set<Integer> seenSizes = new HashSet<>();
            for (Long sid : setIds) {
                SetTypeDto s = sid == null ? null : SetService.get(c, sid);
                if (s == null) throw ApiException.bad("One or more sets were not found.");
                for (int size : s.sizes()) {
                    // overlap rule: one size cannot belong to two sets of the same article
                    if (!seenSizes.add(size)) {
                        throw ApiException.bad("Selected sets overlap on size " + size + " (set " + s.name() + ").");
                    }
                }
                sets.add(s);
            }
            long id = Db.insert(c,
                    "INSERT INTO article (name, article_number, company_name, color, cp, sp) VALUES (?, ?, ?, ?, ?, ?)",
                    r.name().trim(), r.articleNumber(), r.companyName(), r.color(), r.cp(), r.sp());
            for (SetTypeDto s : sets) {
                Db.update(c, "INSERT INTO article_set_type (article_id, set_type_id) VALUES (?, ?)", id, s.id());
            }
            return get(c, id);
        });
    }

    public static List<ArticleDto> findAll() {
        return Db.run(c -> {
            List<ArticleDto> rows = Db.query(c, "SELECT * FROM article ORDER BY id", ArticleService::mapRow);
            List<ArticleDto> out = new ArrayList<>();
            for (ArticleDto a : rows) out.add(withSets(c, a));
            return out;
        });
    }

    public static ArticleDto findById(long id) {
        return Db.run(c -> {
            ArticleDto a = get(c, id);
            if (a == null) throw ApiException.notFound("Article " + id + " not found.");
            return a;
        });
    }

    public static Availability availability(long id) {
        return Db.run(c -> {
            ArticleDto a = get(c, id);
            if (a == null) throw ApiException.notFound("Article " + id + " not found.");
            return availability(c, a);
        });
    }

    /** Null when the article does not exist. */
    static ArticleDto get(Connection c, long id) throws SQLException {
        ArticleDto row = Db.one(c, "SELECT * FROM article WHERE id = ?", ArticleService::mapRow, id);
        return row == null ? null : withSets(c, row);
    }

    private static ArticleDto mapRow(ResultSet rs) throws SQLException {
        return new ArticleDto(rs.getLong("id"), rs.getString("name"), rs.getString("article_number"),
                rs.getString("company_name"), rs.getString("color"),
                rs.getBigDecimal("cp"), rs.getBigDecimal("sp"), List.of());
    }

    private static ArticleDto withSets(Connection c, ArticleDto a) throws SQLException {
        List<SetTypeDto> sets = Db.query(c,
                "SELECT st.id, st.name, st.sizes FROM set_type st " +
                "JOIN article_set_type j ON j.set_type_id = st.id WHERE j.article_id = ? ORDER BY st.id",
                SetService::map, a.id());
        return new ArticleDto(a.id(), a.name(), a.articleNumber(), a.companyName(), a.color(), a.cp(), a.sp(), sets);
    }

    /**
     * Sets and loose pieces are never stored. They are worked out from the size-wise stock:
     *   full sets of a set = the smallest stock among its sizes
     *   loose pieces of a size = its stock minus the sets already taken out of it
     */
    static Availability availability(Connection c, ArticleDto a) throws SQLException {
        Map<Integer, Integer> stock = new TreeMap<>();
        for (int[] row : Db.query(c, "SELECT size, quantity FROM stock WHERE article_id = ?",
                rs -> new int[]{rs.getInt(1), rs.getInt(2)}, a.id())) {
            stock.put(row[0], row[1]);
        }

        Map<Integer, Integer> loose = new TreeMap<>(stock);
        List<SetAvail> sets = new ArrayList<>();
        for (SetTypeDto s : a.setTypes()) {
            int available = Integer.MAX_VALUE;
            for (int size : s.sizes()) available = Math.min(available, stock.getOrDefault(size, 0));
            if (s.sizes().isEmpty()) available = 0;
            sets.add(new SetAvail(s.id(), s.name(), s.sizes(), available));
            for (int size : s.sizes()) loose.merge(size, -available, Integer::sum);
        }

        List<LooseAvail> looseList = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : loose.entrySet()) {
            if (e.getValue() > 0) looseList.add(new LooseAvail(e.getKey(), e.getValue()));
        }
        return new Availability(a.id(), a.name(), sets, looseList);
    }
}
