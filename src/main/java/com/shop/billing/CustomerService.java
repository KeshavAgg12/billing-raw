package com.shop.billing;

import com.shop.billing.Models.Balance;
import com.shop.billing.Models.CustomerDto;
import com.shop.billing.Models.CustomerReq;
import com.shop.billing.Models.PaymentReq;
import com.shop.billing.Models.PaymentRes;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public final class CustomerService {
    private CustomerService() {}

    public static CustomerDto create(CustomerReq r) {
        if (r == null || r.name() == null || r.name().isBlank()) throw ApiException.bad("Customer name is required.");
        return Db.run(c -> {
            long id = Db.insert(c, "INSERT INTO customer (name, phone, address) VALUES (?, ?, ?)",
                    r.name().trim(), r.phone(), r.address());
            return new CustomerDto(id, r.name().trim(), r.phone(), r.address());
        });
    }

    public static List<CustomerDto> findAll() {
        return Db.run(c -> Db.query(c, "SELECT * FROM customer ORDER BY name", CustomerService::map));
    }

    /** Null when the customer does not exist. */
    static CustomerDto find(Connection c, long id) throws SQLException {
        return Db.one(c, "SELECT * FROM customer WHERE id = ?", CustomerService::map, id);
    }

    /** Outstanding = total billed minus total paid. It is calculated, never stored. */
    public static Balance balance(long customerId) {
        return Db.run(c -> {
            CustomerDto cu = find(c, customerId);
            if (cu == null) throw ApiException.notFound("Customer " + customerId + " not found.");
            BigDecimal billed = Db.one(c, "SELECT COALESCE(SUM(total), 0) FROM invoice WHERE customer_id = ?",
                    rs -> rs.getBigDecimal(1), customerId);
            BigDecimal paid = Db.one(c, "SELECT COALESCE(SUM(amount), 0) FROM payment WHERE customer_id = ?",
                    rs -> rs.getBigDecimal(1), customerId);
            return new Balance(cu.id(), cu.name(), billed, paid, billed.subtract(paid));
        });
    }

    public static PaymentRes addPayment(PaymentReq r) {
        if (r == null || r.customerId() == null) throw ApiException.bad("Choose the customer who paid.");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.bad("Amount must be above zero.");
        return Db.run(c -> {
            if (find(c, r.customerId()) == null) throw ApiException.notFound("Customer not found.");
            LocalDate today = LocalDate.now();
            long id = Db.insert(c, "INSERT INTO payment (customer_id, amount, payment_date, note) VALUES (?, ?, ?, ?)",
                    r.customerId(), r.amount(), today, r.note());
            return new PaymentRes(id, r.customerId(), r.amount(), today.toString(), r.note());
        });
    }

    private static CustomerDto map(ResultSet rs) throws SQLException {
        return new CustomerDto(rs.getLong("id"), rs.getString("name"), rs.getString("phone"), rs.getString("address"));
    }
}
