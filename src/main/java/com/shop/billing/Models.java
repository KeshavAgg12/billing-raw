package com.shop.billing;

import java.math.BigDecimal;
import java.util.List;

/** Plain data holders. Records are used both for incoming JSON and outgoing JSON. */
public final class Models {
    private Models() {}

    // ---- incoming requests ----
    public record CreateSetReq(List<Integer> sizes) {}

    public record CreateArticleReq(String name, String articleNumber, String companyName, String color,
                                   BigDecimal cp, BigDecimal sp, List<Long> setTypeIds) {}

    public record PurchaseItemReq(int size, int quantity) {}

    public record PurchaseReq(Long articleId, BigDecimal cp, String purchaseDate, List<PurchaseItemReq> items) {}

    public record CustomerReq(String name, String phone, String address) {}

    public record PaymentReq(Long customerId, BigDecimal amount, String note) {}

    /** type is "SET" (needs setTypeId) or "LOOSE" (needs size). quantity = sets or pairs. */
    public record InvoiceItemReq(Long articleId, String type, Long setTypeId, Integer size, int quantity) {}

    public record InvoiceReq(Long customerId, List<InvoiceItemReq> items) {}

    // ---- outgoing responses ----
    public record SetTypeDto(long id, String name, List<Integer> sizes) {}

    public record ArticleDto(long id, String name, String articleNumber, String companyName, String color,
                             BigDecimal cp, BigDecimal sp, List<SetTypeDto> setTypes) {}

    public record SetAvail(long setTypeId, String setName, List<Integer> sizes, int available) {}

    public record LooseAvail(int size, int quantity) {}

    public record Availability(long articleId, String articleName, List<SetAvail> sets, List<LooseAvail> loose) {}

    public record PurchaseRes(long id, long articleId, String articleName, BigDecimal cp, String purchaseDate,
                              int totalPieces, BigDecimal totalCost) {}

    public record CustomerDto(long id, String name, String phone, String address) {}

    public record Balance(long customerId, String name, BigDecimal totalBilled, BigDecimal totalPaid,
                          BigDecimal outstanding) {}

    public record PaymentRes(long id, long customerId, BigDecimal amount, String paymentDate, String note) {}

    public record InvoiceItemRes(long articleId, String articleName, String type, String setName, Integer size,
                                 int quantity, int pieces, BigDecimal rate, BigDecimal lineTotal) {}

    public record InvoiceRes(long id, long customerId, String customerName, String invoiceDate, BigDecimal total,
                             List<InvoiceItemRes> items, List<String> messages) {}

    public record Summary(String fromDate, String toDate, BigDecimal totalPurchase, BigDecimal totalSales,
                          BigDecimal costOfGoodsSold, BigDecimal profit) {}

    public record Pricing(int bulkThresholdPieces, BigDecimal bulkDiscount, BigDecimal looseExtra) {}

    public record InvoiceSummary(long id, long customerId, String customerName, String invoiceDate, BigDecimal total) {}
}
