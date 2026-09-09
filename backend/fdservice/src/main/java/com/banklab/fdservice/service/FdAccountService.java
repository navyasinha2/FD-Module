package com.banklab.fdservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.banklab.fdservice.client.CustomerDto;
import com.banklab.fdservice.client.CustomerServiceClient;
import com.banklab.fdservice.client.ProductDetails;
import com.banklab.fdservice.client.ProductPricingClient;
import com.banklab.fdservice.client.ProductPricingClientException;
import com.banklab.fdservice.client.RateDetails;
import com.banklab.fdservice.client.UpstreamServiceException.Reason;
import com.banklab.fdservice.dto.AccountStatus;
import com.banklab.fdservice.dto.AssignRoleRequest;
import com.banklab.fdservice.dto.CreateFdAccountRequest;
import com.banklab.fdservice.dto.RoleType;
import com.banklab.fdservice.entity.FdAccount;
import com.banklab.fdservice.entity.FdAccountRole;
import com.banklab.fdservice.repository.FdAccountRepository;
import com.banklab.fdservice.repository.FdAccountRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates FD account creation (openapi createFdAccount): resolves
 * customer/product/rate/category against Groups 1 and 2, mints the account
 * number, computes maturity date, and persists the account plus its initial
 * role(s) in one transaction.
 */
@Service
@RequiredArgsConstructor
public class FdAccountService {

    private static final String DEFAULT_DAY_COUNT_CONVENTION = "ACT/365";

    private final ProductPricingClient productPricingClient;
    private final CustomerServiceClient customerServiceClient;
    private final AccountNumberGeneratorService accountNumberGeneratorService;
    private final FdAccountRepository accountRepository;
    private final FdAccountRoleRepository roleRepository;

    @Value("${fd.account-number.default-branch-cd}")
    private String defaultBranchCd;

    @Transactional
    public com.banklab.fdservice.dto.FdAccount createAccount(CreateFdAccountRequest request) {
        CustomerDto customer = resolveCustomer(request.custId());
        ProductDetails product = resolveProduct(request.productCode());
        RateDetails rate = resolveRate(request.rateId());
        resolveCategory(request.categoryCd());
        validateRoleCardinality(request.initialRoles());

        String accountNumber = accountNumberGeneratorService.generateAccountNumber(defaultBranchCd);
        LocalDate openDt = LocalDate.now();
        LocalDate matDt = openDt.plusMonths(request.tenureMonths());

        FdAccount savedAccount = accountRepository.save(
                buildAccount(request, accountNumber, customer, product, rate, openDt, matDt));
        roleRepository.saveAll(buildRoles(savedAccount.getFdaId(), request.initialRoles(), openDt));

        return toResponse(savedAccount);
    }

    private CustomerDto resolveCustomer(String custId) {
        return customerServiceClient.getCustomer(custId);
    }

    private ProductDetails resolveProduct(String productCode) {
        return productPricingClient.getProduct(productCode);
    }

    private RateDetails resolveRate(String rateId) {
        if (!StringUtils.hasText(rateId)) {
            return null;
        }
        return productPricingClient.getRate(rateId)
                .orElseThrow(() -> new ProductPricingClientException(
                        "Rate not found for rateId=" + rateId, Reason.NOT_FOUND));
    }

    private void resolveCategory(String categoryCd) {
        if (!StringUtils.hasText(categoryCd)) {
            return;
        }
        productPricingClient.getCategory(categoryCd)
                .orElseThrow(() -> new CategoryMappingUnresolvedException(categoryCd));
    }

    private void validateRoleCardinality(List<AssignRoleRequest> initialRoles) {
        requireAtMostOne(initialRoles, RoleType.GUARANTOR);
        requireAtMostOne(initialRoles, RoleType.GUARDIAN);
    }

    private void requireAtMostOne(List<AssignRoleRequest> roles, RoleType roleType) {
        long count = roles.stream().filter(role -> role.roleType() == roleType).count();
        if (count > 1) {
            throw new RoleCardinalityViolationException(roleType);
        }
    }

    private FdAccount buildAccount(CreateFdAccountRequest request, String accountNumber, CustomerDto customer,
            ProductDetails product, RateDetails rate, LocalDate openDt, LocalDate matDt) {
        return FdAccount.builder()
                .fdaId(UUID.randomUUID())
                .acctNum(accountNumber)
                .custId(request.custId())
                .prdCode(request.productCode())
                .rateId(request.rateId())
                .categoryCd(request.categoryCd())
                .ccyCd(firstNonBlank(request.currencyCode(), product.currency()))
                .principalAmt(request.principal())
                .principalBal(request.principal())
                .accruedIntAmt(BigDecimal.ZERO)
                .intRt(rate != null ? rate.effectiveRate() : null)
                .compoundFreq(firstNonBlank(request.compoundingFreq(), product.defaultCompoundingFrequency()))
                .payoutFreq(firstNonBlank(request.payoutFreq(), product.defaultPayoutFrequency()))
                .dayCountConv(DEFAULT_DAY_COUNT_CONVENTION)
                .tenureMonths(request.tenureMonths())
                .openDt(openDt)
                .valueDt(openDt)
                .matDt(matDt)
                .matInstruction(request.maturityInstruction() != null
                        ? request.maturityInstruction().name()
                        : product.defaultMaturityInstruction())
                .sts(AccountStatus.ACTIVE.name())
                .custNameSnap(customer.fullName())
                .prdNameSnap(product.productName())
                .build();
    }

    private List<FdAccountRole> buildRoles(UUID fdaId, List<AssignRoleRequest> initialRoles, LocalDate efctvDt) {
        // Collectors.toList(), not Stream.toList() — Lombok's @SuperBuilder.builder()
        // returns a wildcard-bounded builder type, and only the explicit List<FdAccountRole>
        // target type here (via Collectors.<T>toList() inference) resolves the wildcard
        // capture cleanly; Stream.toList() infers too early and fails to compile.
        return initialRoles.stream()
                .map(role -> FdAccountRole.builder()
                        .fdaId(fdaId)
                        .custId(role.custId())
                        .roleTyp(role.roleType().name())
                        .isPrimary(Boolean.TRUE.equals(role.isPrimary()))
                        .efctvDt(efctvDt)
                        .build())
                .collect(Collectors.toList());
    }

    private String firstNonBlank(String requested, String fallback) {
        return StringUtils.hasText(requested) ? requested : fallback;
    }

    private com.banklab.fdservice.dto.FdAccount toResponse(FdAccount entity) {
        return com.banklab.fdservice.dto.FdAccount.builder()
                .fdaId(entity.getFdaId().toString())
                .acctNum(entity.getAcctNum())
                .custId(entity.getCustId())
                .productCode(entity.getPrdCode())
                .rateId(entity.getRateId())
                .categoryCd(entity.getCategoryCd())
                .currencyCode(entity.getCcyCd())
                .principalAmt(entity.getPrincipalAmt())
                .principalBal(entity.getPrincipalBal())
                .accruedIntAmt(entity.getAccruedIntAmt())
                .intRt(entity.getIntRt())
                .compoundFreq(entity.getCompoundFreq())
                .payoutFreq(entity.getPayoutFreq())
                .dayCountConv(entity.getDayCountConv())
                .tenureMonths(entity.getTenureMonths())
                .openDt(entity.getOpenDt())
                .valueDt(entity.getValueDt())
                .matDt(entity.getMatDt())
                .matAmt(entity.getMatAmt())
                .matInstruction(parseEnum(com.banklab.fdservice.dto.MaturityInstruction.class, entity.getMatInstruction()))
                .status(parseEnum(AccountStatus.class, entity.getSts()))
                .renewedFromId(entity.getRenewedFromId() != null ? entity.getRenewedFromId().toString() : null)
                .custNameSnap(entity.getCustNameSnap())
                .prdNameSnap(entity.getPrdNameSnap())
                .build();
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
