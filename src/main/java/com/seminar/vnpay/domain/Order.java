package com.seminar.vnpay.domain;

import java.time.Instant;

/** Don hang toi gian. Trong microservice that: 1 bang trong DB + cot version de optimistic lock. */
public class Order {

    public enum Status { PENDING, PAID, FAILED }

    private final String txnRef;      // vnp_TxnRef - duy nhat trong 24h theo TmnCode
    private final long amount;        // VND, chua nhan 100
    private final String orderInfo;
    private final Instant createdAt = Instant.now();

    private volatile Status status = Status.PENDING;
    private volatile String transactionNo;   // ma giao dich ben VNPAY
    private volatile String responseCode;
    private volatile String bankCode;
    private volatile String payDate;

    public Order(String txnRef, long amount, String orderInfo) {
        this.txnRef = txnRef;
        this.amount = amount;
        this.orderInfo = orderInfo;
    }

    public String getTxnRef() { return txnRef; }
    public long getAmount() { return amount; }
    public String getOrderInfo() { return orderInfo; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getTransactionNo() { return transactionNo; }
    public void setTransactionNo(String transactionNo) { this.transactionNo = transactionNo; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public String getPayDate() { return payDate; }
    public void setPayDate(String payDate) { this.payDate = payDate; }
}
