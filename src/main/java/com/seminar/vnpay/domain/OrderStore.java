package com.seminar.vnpay.domain;

import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Dự án thật thay chỗ này bằng JpaRepository. */
@Repository
public class OrderStore {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public void save(Order order) {
        orders.put(order.getTxnRef(), order);
    }

    public Optional<Order> findByTxnRef(String txnRef) {
        return Optional.ofNullable(orders.get(txnRef));
    }

    public Collection<Order> findAll() {
        return orders.values();
    }
}
