package com.shop.billing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** All JDBC plumbing in one place: connection, transaction, and small query helpers. */
public final class Db {
    private static String url;

    private Db() {}

    public interface Work<T> { T run(Connection c) throws SQLException; }

    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    /** Opens the database file and creates the tables from schema.sql if they do not exist. */
    public static void init(String path) {
        url = "jdbc:h2:file:" + path;
        String schema = readResource("/schema.sql");
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement st = c.createStatement()) {
            for (String sql : schema.split(";")) {
                if (!sql.isBlank()) st.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot set up the database: " + e.getMessage(), e);
        }
    }

    /**
     * Runs the work in one transaction. If anything throws, everything is rolled back,
     * so a half-finished invoice never leaves stock reduced.
     */
    public static <T> T run(Work<T> work) {
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    public static <T> List<T> query(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        }
    }

    /** First row or null. */
    public static <T> T one(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> rows = query(c, sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** Insert and return the generated id. */
    public static long insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static String readResource(String path) {
        try (InputStream in = Db.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException(path + " not found");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
