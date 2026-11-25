package com.shop.billing;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Properties;

/** Reads config.properties (port, database file, pricing rules). */
public final class Config {
    private static final Properties P = new Properties();

    static {
        try (InputStream in = Config.class.getResourceAsStream("/config.properties")) {
            if (in == null) throw new IllegalStateException("config.properties not found");
            P.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Config() {}

    public static int port() { return Integer.parseInt(P.getProperty("server.port", "8081").trim()); }

    public static String dbPath() { return P.getProperty("db.path", "./data/billingraw").trim(); }

    public static int bulkThresholdPieces() {
        return Integer.parseInt(P.getProperty("pricing.bulk-threshold-pieces", "32").trim());
    }

    public static BigDecimal bulkDiscount() { return new BigDecimal(P.getProperty("pricing.bulk-discount", "1").trim()); }

    public static BigDecimal looseExtra() { return new BigDecimal(P.getProperty("pricing.loose-extra", "5").trim()); }
}
