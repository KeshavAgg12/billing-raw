package com.shop.billing;

import com.shop.billing.Models.SetTypeDto;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/** Master list of sets, e.g. 5-8 or 11-2. Sizes are stored in order as "11,12,13,1,2". */
public final class SetService {
    private SetService() {}

    public static SetTypeDto create(List<Integer> sizes) {
        if (sizes == null || sizes.isEmpty()) throw ApiException.bad("Add at least one size to the set.");
        for (Integer s : sizes) {
            if (s == null || s <= 0) throw ApiException.bad("Sizes must be whole numbers above zero.");
        }
        if (new HashSet<>(sizes).size() != sizes.size()) throw ApiException.bad("A size is repeated in this set.");

        String name = sizes.get(0) + "-" + sizes.get(sizes.size() - 1);   // 5-8, 11-2
        String csv = sizes.stream().map(String::valueOf).collect(Collectors.joining(","));

        return Db.run(c -> {
            Long existing = Db.one(c, "SELECT id FROM set_type WHERE name = ?", rs -> rs.getLong(1), name);
            if (existing != null) throw ApiException.conflict("Set " + name + " already exists.");
            long id = Db.insert(c, "INSERT INTO set_type (name, sizes) VALUES (?, ?)", name, csv);
            return new SetTypeDto(id, name, sizes);
        });
    }

    public static List<SetTypeDto> findAll() {
        return Db.run(c -> Db.query(c, "SELECT id, name, sizes FROM set_type ORDER BY id", SetService::map));
    }

    /** Returns null when the set does not exist. */
    public static SetTypeDto get(Connection c, long id) throws SQLException {
        return Db.one(c, "SELECT id, name, sizes FROM set_type WHERE id = ?", SetService::map, id);
    }

    static SetTypeDto map(ResultSet rs) throws SQLException {
        List<Integer> sizes = Arrays.stream(rs.getString("sizes").split(","))
                .map(String::trim).map(Integer::parseInt).toList();
        return new SetTypeDto(rs.getLong("id"), rs.getString("name"), sizes);
    }
}
