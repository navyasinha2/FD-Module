package com.banklab.fdservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banklab.fdservice.dto.AccountStatus;
import com.banklab.fdservice.dto.FdTxnType;
import com.banklab.fdservice.dto.PrematureWithdrawalRequest;
import com.banklab.fdservice.dto.PrematureWithdrawalResponse;
import com.banklab.fdservice.entity.FdAccount;
import com.banklab.fdservice.repository.FdAccountRepository;
import com.banklab.fdservice.repository.FdTransactionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Premature closure (OpenAPI processEarlyWithdrawal, lab manual L15), atomically:
 * accrue up to the business date, credit that interest, debit the penalty, pay the
 * rest out as WITHDRAWAL, and close the account as CLOSED. All postings share one
 * FDT_TXN_GRP_ID. Only full withdrawal is supported.
 */
@Service
@RequiredArgsConstructor
public class PrematureWithdrawalService {

    private final FdAccountRepository accountRepository;
    private final FdTransactionRepository transactionRepository;
    private final BusinessClockService businessClock;
    private final InterestPostingService interestPosting;
    private final PrematureWithdrawalPolicy policy;
    private final FdLedgerService ledger;

    @Transactional
    public PrematureWithdrawalResponse withdraw(UUID fdaId, PrematureWithdrawalRequest request) {
        FdAccount account = accountRepository.lockById(fdaId).orElseThrow(() -> new FdAccountNotFoundException(fdaId));
        LocalDate withdrawalDate = businessClock.currentBusinessDate();
        policy.validateEligibility(account.getSts(), account.getValueDt(), account.getMatDt(), withdrawalDate);
        requireAccrualNotAhead(account, withdrawalDate);
        requireFullWithdrawal(account, request);

        String userId = FdLedgerService.API_USER;
        UUID group = UUID.randomUUID();
        BigDecimal finalInterest = BigDecimal.ZERO;
        int currencyDecimals;
        if (interestPosting.hasContractedRate(account)) {
            interestPosting.bringAccrualTo(account, withdrawalDate, withdrawalDate, userId);
            finalInterest = interestPosting.settleAccruedInterest(account, withdrawalDate, withdrawalDate, group,
                    userId);
            currencyDecimals = interestPosting.termsOf(account).currencyDecimals();
        } else {
            // Booked without a rate: no interest to credit, but the deposit can still be closed.
            currencyDecimals = account.getCcyDecimals() != null
                    ? account.getCcyDecimals()
                    : CurrencyDecimals.forCurrency(account.getCcyCd());
        }

        BigDecimal interestEarned = transactionRepository.sumAmountByType(fdaId, FdTxnType.INTEREST.name());
        BigDecimal penalty = policy.penalty(interestEarned, account.getPrincipalBal(), currencyDecimals);
        if (penalty.signum() > 0) {
            ledger.postPenalty(account, penalty, withdrawalDate, group, userId);
        }

        BigDecimal payout = account.getPrincipalBal();
        if (payout.signum() > 0) {
            ledger.postWithdrawal(account, payout, withdrawalDate, group, userId);
        }

        account.setSts(AccountStatus.CLOSED.name());
        account.setClosureDt(withdrawalDate);
        account.stampAudit(userId, "U");

        return new PrematureWithdrawalResponse(
                "success",
                "FD account closed before maturity",
                withdrawalDate,
                interestEarned.setScale(currencyDecimals, RoundingMode.HALF_UP),
                finalInterest,
                penalty,
                payout,
                FdAccountMapper.toResponse(account));
    }

    /**
     * The withdrawal day itself earns no interest. If interest for that day or later is
     * already accrued (the business clock was moved back after an EOD), closing now would
     * pay interest for days the money isn't held.
     */
    private static void requireAccrualNotAhead(FdAccount account, LocalDate withdrawalDate) {
        if (account.getLastAccrualDt() != null && !account.getLastAccrualDt().isBefore(withdrawalDate)) {
            throw new AccountNotWithdrawableException(AccountNotWithdrawableException.ACCRUAL_AHEAD_OF_BUSINESS_DATE,
                    "Interest is already accrued for " + account.getLastAccrualDt()
                            + ", on or after the business date " + withdrawalDate);
        }
    }

    private static void requireFullWithdrawal(FdAccount account, PrematureWithdrawalRequest request) {
        if (request == null || request.amount() == null) {
            return;
        }
        if (request.amount().compareTo(account.getPrincipalBal()) != 0) {
            throw new AccountNotWithdrawableException(AccountNotWithdrawableException.PARTIAL_NOT_SUPPORTED,
                    "Only full premature withdrawal is supported; omit amount or pass the full balance "
                            + account.getPrincipalBal());
        }
    }
}
