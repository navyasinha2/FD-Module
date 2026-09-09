package com.banklab.fdservice.client;

/**
 * Projection of Group 2's PRODUCTS row needed to book an FD account. Snapshotted
 * onto FD_ACCOUNTS at creation time (ERD principle P1) — never re-fetched afterwards.
 */
public record ProductDetails(
        String productCode,
        String productName,
        String currency,
        String defaultCompoundingFrequency,
        String defaultPayoutFrequency,
        String defaultMaturityInstruction) {
}
