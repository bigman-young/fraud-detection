package com.hsbc.fraud.model;

/**
 * Enumeration of transaction types supported by the fraud detection system.
 */
public enum TransactionType {
    TRANSFER,      // Money transfer between accounts
    WITHDRAWAL,    // Cash withdrawal
    DEPOSIT,       // Cash deposit
    PAYMENT,       // Payment to merchant
    REFUND,        // Refund transaction
    PURCHASE,      // Online/offline purchase
    WIRE_TRANSFER, // International wire transfer
    BILL_PAYMENT   // Bill payment
}

