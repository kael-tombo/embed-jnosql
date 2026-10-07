package org.embeddedjnosql.db.demo.batch.model;

import java.time.Instant;
import java.util.Map;

/**
 * Domain model representing a financial transaction for batch ingestion demonstrations.
 */
public record FinancialTransaction(
        String id,
        String accountId,
        String counterpartyAccount,
        double amount,
        String currency,
        String transactionType, // CREDIT, DEBIT, TRANSFER, FEE
        String status,          // PENDING, SETTLED, REJECTED
        long timestamp
) {
    public static FinancialTransaction of(String id, String accountId, String counterparty, double amount, String type) {
        return new FinancialTransaction(id, accountId, counterparty, amount, "USD", type, "SETTLED", System.currentTimeMillis());
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "id", id,
                "accountId", accountId,
                "counterpartyAccount", counterpartyAccount,
                "amount", amount,
                "currency", currency,
                "transactionType", transactionType,
                "status", status,
                "timestamp", timestamp
        );
    }
}
