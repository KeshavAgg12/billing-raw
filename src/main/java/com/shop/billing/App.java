package com.shop.billing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** Shoe Bazaar: plain Java backend (JDK HttpServer + JDBC). Also serves the web screens from /static. */
public final class App {
    private App() {}

    public static void main(String[] args) throws IOException {
        Db.init(Config.dbPath());

        HttpServer server = HttpServer.create(new InetSocketAddress(Config.port()), 0);
        server.createContext("/", App::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Shoe Bazaar is running at http://localhost:" + Config.port());
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
                Api.Result result = Api.route(ex.getRequestMethod(), path, query, body);
                sendJson(ex, result.status(), result.body());
            } else {
                serveStatic(ex, path);
            }
        } catch (ApiException e) {
            sendJson(ex, e.status, Map.of("message", String.valueOf(e.getMessage())));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("message", "Server error: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Api.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void serveStatic(HttpExchange ex, String path) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) {
            sendText(ex, 405, "Method not allowed");
            return;
        }
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..")) {
            sendText(ex, 404, "Not found");
            return;
        }
        try (InputStream in = App.class.getResourceAsStream("/static" + path)) {
            if (in == null) {
                sendText(ex, 404, "Not found");
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType(path));
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private static void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return out;
    }
}
