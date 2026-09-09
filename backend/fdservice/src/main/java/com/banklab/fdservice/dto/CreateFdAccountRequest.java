package com.banklab.fdservice.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Matches openapi schema CreateFdAccountRequest (POST /fd-accounts). productCode /
 * rateId / categoryCd are resolved against the Product & Pricing service at
 * creation time and snapshotted onto FD_ACCOUNTS — see client.ProductPricingClient.
 */
public record CreateFdAccountRequest(
        @NotBlank String custId,
        @NotBlank @Size(max = 20, message = "must be at most 20 characters") String productCode,
        @NotNull @Positive BigDecimal principal,
        @NotNull @Positive Integer tenureMonths,
        String rateId,
        String categoryCd,
        String currencyCode,
        String payoutFreq,
        String compoundingFreq,
        MaturityInstruction maturityInstruction,
        @NotEmpty List<@Valid AssignRoleRequest> initialRoles) {
}
