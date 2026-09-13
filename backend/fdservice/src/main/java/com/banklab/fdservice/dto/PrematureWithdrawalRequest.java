package com.banklab.fdservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;

/**
 * Request body of POST /fd-accounts/{accountId}/withdraw. Only full withdrawal is
 * supported: amount may be omitted, or must equal the current balance.
 */
public record PrematureWithdrawalRequest(@Positive BigDecimal amount, String remarks) {
}
