package com.shop.billing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.shop.billing.Models.CreateArticleReq;
import com.shop.billing.Models.CreateSetReq;
import com.shop.billing.Models.CustomerReq;
import com.shop.billing.Models.InvoiceReq;
import com.shop.billing.Models.PaymentReq;
import com.shop.billing.Models.PurchaseReq;

import java.util.Map;

/** The routing table: which URL and method calls which service. */
final class Api {
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    record Result(int status, Object body) {}

    private Api() {}

    static Result route(String method, String path, Map<String, String> query, String body) {
        String[] p = path.substring("/api/".length()).split("/");
        int n = p.length;
        boolean get = method.equals("GET");
        boolean post = method.equals("POST");

        switch (p[0]) {
            case "set-types" -> {
                if (n == 1 && get) return ok(SetService.findAll());
                if (n == 1 && post) return created(SetService.create(parse(body, CreateSetReq.class).sizes()));
            }
            case "articles" -> {
                if (n == 1 && get) return ok(ArticleService.findAll());
                if (n == 1 && post) return created(ArticleService.create(parse(body, CreateArticleReq.class)));
                if (n == 2 && get) return ok(ArticleService.findById(id(p[1])));
                if (n == 3 && get && p[2].equals("availability")) return ok(ArticleService.availability(id(p[1])));
            }
            case "purchases" -> {
                if (n == 1 && post) return created(StockService.addStock(parse(body, PurchaseReq.class)));
            }
            case "customers" -> {
                if (n == 1 && get) return ok(CustomerService.findAll());
                if (n == 1 && post) return created(CustomerService.create(parse(body, CustomerReq.class)));
                if (n == 3 && get && p[2].equals("balance")) return ok(CustomerService.balance(id(p[1])));
            }
            case "invoices" -> {
                if (n == 1 && get) return ok(InvoiceService.search(query.get("customer")));
                if (n == 1 && post) return created(InvoiceService.create(parse(body, InvoiceReq.class)));
                if (n == 2 && get) return ok(InvoiceService.findById(id(p[1])));
            }
            case "payments" -> {
                if (n == 1 && post) return created(CustomerService.addPayment(parse(body, PaymentReq.class)));
            }
            case "pricing" -> {
                if (n == 1 && get) {
                    return ok(new Models.Pricing(Config.bulkThresholdPieces(), Config.bulkDiscount(), Config.looseExtra()));
                }
            }
            case "reports" -> {
                if (n == 2 && get && p[1].equals("summary")) {
                    return ok(ReportService.summary(query.get("from"), query.get("to")));
                }
            }
            default -> { }
        }
        throw new ApiException(404, "No such endpoint: " + method + " " + path);
    }

    private static Result ok(Object body) { return new Result(200, body); }

    private static Result created(Object body) { return new Result(201, body); }

    private static long id(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw ApiException.bad("'" + text + "' is not a valid id.");
        }
    }

    private static <T> T parse(String body, Class<T> type) {
        if (body == null || body.isBlank()) throw ApiException.bad("The request body is empty.");
        try {
            T value = GSON.fromJson(body, type);
            if (value == null) throw ApiException.bad("The request body is empty.");
            return value;
        } catch (JsonParseException e) {
            throw ApiException.bad("The request is not valid JSON for this action.");
        }
    }
}
